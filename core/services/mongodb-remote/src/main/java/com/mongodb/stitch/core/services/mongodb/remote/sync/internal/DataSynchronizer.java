/*
 * Copyright 2018-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.stitch.core.services.mongodb.remote.sync.internal;

import static com.mongodb.stitch.core.services.mongodb.remote.sync.internal.ChangeEvent.changeEventForLocalDelete;
import static com.mongodb.stitch.core.services.mongodb.remote.sync.internal.ChangeEvent.changeEventForLocalInsert;
import static com.mongodb.stitch.core.services.mongodb.remote.sync.internal.ChangeEvent.changeEventForLocalReplace;
import static com.mongodb.stitch.core.services.mongodb.remote.sync.internal.ChangeEvent.changeEventForLocalUpdate;

import com.mongodb.Block;
import com.mongodb.Function;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoNamespace;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.FindOneAndReplaceOptions;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.lang.NonNull;
import com.mongodb.stitch.core.StitchServiceErrorCode;
import com.mongodb.stitch.core.StitchServiceException;
import com.mongodb.stitch.core.internal.common.AuthMonitor;
import com.mongodb.stitch.core.internal.common.BsonUtils;
import com.mongodb.stitch.core.internal.common.Callback;
import com.mongodb.stitch.core.internal.common.Dispatcher;
import com.mongodb.stitch.core.internal.net.NetworkMonitor;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoClient;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoCollection;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ErrorListener;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;
import org.bson.diagnostics.Logger;
import org.bson.diagnostics.Loggers;

/**
 * DataSynchronizer handles the bidirectional synchronization of documents between a local MongoDB
 * and a remote MongoDB (via Stitch). It also expose CRUD operations to interact with synchronized
 * documents.
 */
public class DataSynchronizer implements NetworkMonitor.StateListener {

  static final String DOCUMENT_VERSION_FIELD = "__stitch_sync_version";

  private final CoreStitchServiceClient service;
  private final CoreRemoteMongoClient remoteClient;
  private final NetworkMonitor networkMonitor;
  private final AuthMonitor authMonitor;
  private final Logger logger;
  private final Lock syncLock;
  private final String instanceKey;

  private MongoClient localClient;
  private MongoDatabase configDb;
  private MongoCollection<InstanceSynchronizationConfig> instancesColl;
  private InstanceChangeStreamListener instanceChangeStreamListener;
  private InstanceSynchronizationConfig syncConfig;

  private boolean syncThreadEnabled = true;
  private boolean isConfigured = false;
  private boolean isRunning = false;
  private Thread syncThread;
  private long logicalT = 0; // The current logical time or sync iteration.

  private final Lock listenersLock;
  private final Dispatcher eventDispatcher;

  private ErrorListener errorListener;

  public DataSynchronizer(
      final String instanceKey,
      final CoreStitchServiceClient service,
      final MongoClient localClient,
      final CoreRemoteMongoClient remoteClient,
      final NetworkMonitor networkMonitor,
      final AuthMonitor authMonitor,
      final Dispatcher eventDispatcher
  ) {
    this.service = service;
    this.localClient = localClient;
    this.remoteClient = remoteClient;
    this.networkMonitor = networkMonitor;
    this.authMonitor = authMonitor;
    this.syncLock = new ReentrantLock();
    this.listenersLock = new ReentrantLock();
    this.eventDispatcher = eventDispatcher;
    this.instanceKey = instanceKey;

    this.logger =
        Loggers.getLogger(String.format("DataSynchronizer-%s", instanceKey));
    if (this.networkMonitor != null) {
      this.networkMonitor.addNetworkStateListener(this);
    }

    initialize();
    final Semaphore recoveryStarted = new Semaphore(0);
    new Thread(() -> recover(recoveryStarted)).start();
    try {
      // Wait to return after the thread has confirmed it has started.
      recoveryStarted.acquire();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void initialize() {
    this.configDb =
        localClient.getDatabase("sync_config" + instanceKey)
            .withCodecRegistry(CodecRegistries.fromRegistries(
                CodecRegistries.fromCodecs(
                    InstanceSynchronizationConfig.configCodec,
                    NamespaceSynchronizationConfig.configCodec,
                    CoreDocumentSynchronizationConfig.configCodec),
                BsonUtils.DEFAULT_CODEC_REGISTRY));

    this.instancesColl = configDb
        .getCollection("instances", InstanceSynchronizationConfig.class);

    if (instancesColl.countDocuments() == 0) {
      this.syncConfig = new InstanceSynchronizationConfig(configDb);
      instancesColl.insertOne(this.syncConfig);
    } else {
      if (instancesColl.find().first() == null) {
        throw new IllegalStateException("expected to find instance configuration");
      }
      this.syncConfig = new InstanceSynchronizationConfig(configDb);
    }
    this.instanceChangeStreamListener = new InstanceChangeStreamListenerImpl(
        syncConfig,
        service,
        networkMonitor,
        authMonitor);
    for (final MongoNamespace ns : this.syncConfig.getSynchronizedNamespaces()) {
      this.instanceChangeStreamListener.addNamespace(ns);
    }
  }

  /**
   * Recovers the state of synchronization in case a system failure happened. The goal is to revert
   * to a known, good state.
   */
  private void recover(final Semaphore recoveryStarted) {
    final List<NamespaceSynchronizationConfig> nsConfigs = new ArrayList<>();
    for (final MongoNamespace ns : this.syncConfig.getSynchronizedNamespaces()) {
      nsConfigs.add(this.syncConfig.getNamespaceConfig(ns));
    }
    for (final NamespaceSynchronizationConfig nsConfig : nsConfigs) {
      nsConfig.getLock().writeLock().lock();
    }
    recoveryStarted.release();
    try {
      for (final NamespaceSynchronizationConfig nsConfig : nsConfigs) {
        recoverNamespace(nsConfig);
      }
    } finally {
      for (final NamespaceSynchronizationConfig nsConfig : nsConfigs) {
        nsConfig.getLock().writeLock().unlock();
      }
    }
  }

  /**
   * Recovers the state of synchronization for a namespace in case a system failure happened.
   * The goal is to revert the namespace to a known, good state. This method itself is resilient
   * to failures, since it doesn't delete any documents from the undo collection until the
   * collection is in the desired state with respect to those documents.
   */
  private void recoverNamespace(final NamespaceSynchronizationConfig nsConfig) {
    final MongoCollection<BsonDocument> undoCollection =
        getUndoCollection(nsConfig.getNamespace());
    final MongoCollection<BsonDocument> localCollection =
        getLocalCollection(nsConfig.getNamespace());
    final List<BsonDocument> undoDocs =
        undoCollection.find().into(new ArrayList<>());
    final Set<BsonValue> recoveredIds = new HashSet<>();

    // Replace local docs with undo docs. Presence of an undo doc implies we had a system failure
    // during a write. This covers updates and deletes.
    for (final BsonDocument undoDoc : undoDocs) {
      final BsonValue documentId = BsonUtils.getDocumentId(undoDoc);
      final BsonDocument filter = getDocumentIdFilter(documentId);
      localCollection.findOneAndReplace(
          filter, undoDoc, new FindOneAndReplaceOptions().upsert(true));
      recoveredIds.add(documentId);
    }

    // If we recovered a document, but its pending writes are set to do something else, then the
    // failure occurred after the pending writes were set, but before the undo document was
    // deleted. In this case, we should restore the document to the state that the pending
    // write indicates. There is a possibility that the pending write is from before the failed
    // operation, but in that case, the findOneAndReplace or delete is a no-op since restoring
    // the document to the state of the change event would be the same as recovering the undo
    // document.
    for (final CoreDocumentSynchronizationConfig docConfig : nsConfig.getSynchronizedDocuments()) {
      final BsonValue documentId = docConfig.getDocumentId();
      final BsonDocument filter = getDocumentIdFilter(documentId);

      if (recoveredIds.contains(docConfig.getDocumentId())) {
        final ChangeEvent<BsonDocument> pendingWrite = docConfig.getLastUncommittedChangeEvent();
        if (pendingWrite != null) {
          switch (pendingWrite.getOperationType()) {
            case INSERT:
            case UPDATE:
            case REPLACE:
              localCollection.findOneAndReplace(
                      filter,
                      pendingWrite.getFullDocument(),
                      new FindOneAndReplaceOptions().upsert(true)
              );
              break;
            case DELETE:
              localCollection.deleteOne(filter);
              break;
            default:
              // There should never be pending writes with an unknown event type, but if someone
              // is messing with the config collection we want to stop the synchronizer to prevent
              // further data corruption.
              throw new IllegalStateException(
                      "there should not be a pending write with an unknown event type"
              );
          }
        }
      }
    }

    // Delete all of our undo documents. If we've reached this point, we've recovered the local
    // collection to the state we want with respect to all of our undo documents. If we fail before
    // these deletes or while carrying out the deletes, but after recovering the documents to
    // their desired state, that's okay because the next recovery pass will be effectively a no-op
    // up to this point.
    for (final BsonValue recoveredId : recoveredIds) {
      undoCollection.deleteOne(getDocumentIdFilter(recoveredId));
    }

    // Find local documents for which there are no document configs and delete them. This covers
    // inserts, upserts, and desync deletes. This will occur on any recovery pass regardless of
    // the documents in the undo collection, so it's fine that we do this after deleting the undo
    // documents.
    localCollection.deleteMany(new BsonDocument(
        "_id",
        new BsonDocument(
            "$nin",
            new BsonArray(new ArrayList<>(getSynchronizedDocumentIds(nsConfig.getNamespace()))))));
  }

  @Override
  public void onNetworkStateChanged() {
    if (!this.networkMonitor.isConnected()) {
      this.stop();
    } else {
      this.start();
    }
  }

  public void reinitialize(final MongoClient localClient) {
    syncLock.lock();
    try {
      this.instanceChangeStreamListener.stop();
      this.stop();
      this.localClient = localClient;
      initialize();
      this.start();
    } finally {
      syncLock.unlock();
    }
  }

  /**
   * Reloads the synchronization config. This wipes all in-memory synchronization settings.
   */
  public void wipeInMemorySettings() {
    syncLock.lock();
    try {
      this.instanceChangeStreamListener.stop();
      if (instancesColl.find().first() == null) {
        throw new IllegalStateException("expected to find instance configuration");
      }
      this.syncConfig = new InstanceSynchronizationConfig(configDb);
      this.instanceChangeStreamListener = new InstanceChangeStreamListenerImpl(
          syncConfig,
          service,
          networkMonitor,
          authMonitor
      );
      this.isConfigured = false;
      this.stop();
    } finally {
      syncLock.unlock();
    }
  }

  public <T> void configure(@Nonnull final MongoNamespace namespace,
                            @Nullable final ConflictHandler<T> conflictHandler,
                            @Nullable final ChangeEventListener<T> changeEventListener,
                            @Nullable final ErrorListener errorListener,
                            @Nonnull final Codec<T> codec) {
    if (conflictHandler == null) {
      logger.warn(
          "Invalid configuration: conflictHandler should not be null. "
              + "The DataSynchronizer will not begin syncing until a ConflictHandler has been "
              + "provided.");
      return;
    }

    this.errorListener = errorListener;

    this.syncConfig.getNamespaceConfig(namespace).configure(
        conflictHandler,
        changeEventListener,
        codec
    );

    syncLock.lock();
    if (!this.isConfigured) {
      this.isConfigured = true;
      syncLock.unlock();
      this.triggerListeningToNamespace(namespace);
    } else {
      syncLock.unlock();
    }

    if (!isRunning) {
      this.start();
    }
  }

  /**
   * Starts data synchronization in a background thread.
   */
  public void start() {
    syncLock.lock();
    try {
      if (!this.isConfigured) {
        return;
      }
      instanceChangeStreamListener.stop();
      instanceChangeStreamListener.start();
      if (syncThread == null) {
        syncThread = new Thread(new DataSynchronizerRunner(
            new WeakReference<>(this),
            networkMonitor,
            logger));
      }
      if (syncThreadEnabled && !isRunning) {
        syncThread.start();
        isRunning = true;
      }
    } finally {
      syncLock.unlock();
    }
  }

  public void disableSyncThread() {
    syncLock.lock();
    try {
      syncThreadEnabled = false;
    } finally {
      syncLock.unlock();
    }
  }

  /**
   * Stops the background data synchronization thread.
   */
  public void stop() {
    syncLock.lock();
    try {
      if (syncThread == null) {
        return;
      }
      instanceChangeStreamListener.stop();
      syncThread.interrupt();
      try {
        syncThread.join();
      } catch (final InterruptedException e) {
        return;
      }
      syncThread = null;
      isRunning = false;
    } finally {
      syncLock.unlock();
    }
  }

  /**
   * Stops the background data synchronization thread and releases the local client.
   */
  public void close() {
    syncLock.lock();
    try {
      if (this.networkMonitor != null) {
        this.networkMonitor.removeNetworkStateListener(this);
      }
      this.eventDispatcher.close();
      stop();
      this.localClient.close();
    } finally {
      syncLock.unlock();
    }
  }

  // ---- Core Synchronization Logic -----

  /**
   * Performs a single synchronization pass in both the local and remote directions; the order
   * of which does not matter. If switching the order produces different results after one pass,
   * then there is a bug.
   *
   * @return whether or not the synchronization pass was successful.
   */
  public boolean doSyncPass() {
    if (!this.isConfigured || !syncLock.tryLock()) {
      return false;
    }
    try {
      if (logicalT == Long.MAX_VALUE) {
        logger.info("reached max logical time; resetting back to 0");
        logicalT = 0;
      }
      logicalT++;

      logger.info(String.format(
          Locale.US,
          "t='%d': doSyncPass START",
          logicalT));
      if (networkMonitor == null || !networkMonitor.isConnected()) {
        logger.info(String.format(
            Locale.US,
            "t='%d': doSyncPass END - Network disconnected",
            logicalT));
        return false;
      }
      if (authMonitor == null || !authMonitor.isLoggedIn()) {
        logger.info(String.format(
            Locale.US,
            "t='%d': doSyncPass END - Logged out",
            logicalT));
        return false;
      }

      syncRemoteToLocal();
      syncLocalToRemote();

      logger.info(String.format(
          Locale.US,
          "t='%d': doSyncPass END",
          logicalT));
    } finally {
      syncLock.unlock();
    }
    return true;
  }

  /**
   * Synchronizes the remote state of every requested document to be synchronized with the local
   * state of said documents. Utilizes change streams to get "recent" updates to documents of
   * interest. Documents that are being synchronized from the first time will be fetched via a
   * full document lookup. Documents that have gone stale will be updated via change events or
   * latest documents with the remote. Any conflicts that occur will be resolved locally and
   * later relayed remotely on a subsequent iteration of {@link DataSynchronizer#doSyncPass()}.
   */
  private void syncRemoteToLocal() {
    logger.info(String.format(
        Locale.US,
        "t='%d': syncRemoteToLocal START",
        logicalT));

    // 2. Run remote to local (R2L) sync routine
    for (final NamespaceSynchronizationConfig nsConfig : syncConfig) {
      nsConfig.getLock().writeLock().lock();
      try {
        final Map<BsonValue, ChangeEvent<BsonDocument>> remoteChangeEvents =
            getEventsForNamespace(nsConfig.getNamespace());

        final Set<BsonValue> unseenIds = nsConfig.getStaleDocumentIds();
        final Set<BsonDocument> latestDocumentsFromStale =
            getLatestDocumentsForStaleFromRemote(nsConfig, unseenIds);
        final Map<BsonValue, BsonDocument> latestDocumentMap = new HashMap<>();

        for (final BsonDocument latestDocument : latestDocumentsFromStale) {
          latestDocumentMap.put(latestDocument.get("_id"), latestDocument);
        }

        // a. For each unprocessed change event
        for (final Map.Entry<BsonValue, ChangeEvent<BsonDocument>> eventEntry :
            remoteChangeEvents.entrySet()) {
          logger.info(String.format(
              Locale.US,
              "t='%d': syncRemoteToLocal consuming event of type: %s",
              logicalT,
              eventEntry.getValue().getOperationType()));

          // i. Find the corresponding local document config.
          final CoreDocumentSynchronizationConfig docConfig =
              nsConfig.getSynchronizedDocument(
                  BsonUtils.getDocumentId(eventEntry.getValue().getDocumentKey()));

          if (docConfig == null || docConfig.isPaused()) {
            // Not interested in this event.
            continue;
          }

          unseenIds.remove(docConfig.getDocumentId());
          latestDocumentMap.remove(docConfig.getDocumentId());
          syncRemoteChangeEventToLocal(nsConfig, docConfig, eventEntry.getValue());
        }

        // For synchronized documents that had no unprocessed change event, but were marked as
        // stale, synthesize a remote replace event to replace the local stale document with the
        // latest remote copy.
        for (final BsonValue docId : unseenIds) {
          final CoreDocumentSynchronizationConfig docConfig =
              nsConfig.getSynchronizedDocument(docId);
          if (docConfig == null || docConfig.isPaused()) {
            // means we aren't actually synchronizing on this remote doc
            continue;
          }

          if (latestDocumentMap.containsKey(docId)) {
            syncRemoteChangeEventToLocal(
                nsConfig,
                docConfig,
                changeEventForLocalReplace(
                    nsConfig.getNamespace(),
                    docId,
                    latestDocumentMap.get(docId),
                    false
                ));

            docConfig.setStale(false);
          }
        }

        // For synchronized documents that had no unprocessed change event, and did not have a
        // latest version when stale documents were queried, synthesize a remote delete event to
        // delete the local document.
        unseenIds.removeAll(latestDocumentMap.keySet());
        for (final BsonValue unseenId : unseenIds) {
          final CoreDocumentSynchronizationConfig docConfig =
              nsConfig.getSynchronizedDocument(unseenId);
          if (docConfig == null
              || docConfig.getLastKnownRemoteVersion() == null
              || docConfig.isPaused()) {
            // means we aren't actually synchronizing on this remote doc
            continue;
          }

          syncRemoteChangeEventToLocal(
              nsConfig,
              docConfig,
              changeEventForLocalDelete(
                  nsConfig.getNamespace(),
                  unseenId,
                  docConfig.hasUncommittedWrites()
              ));

          docConfig.setStale(false);
        }
      } finally {
        nsConfig.getLock().writeLock().unlock();
      }
    }

    logger.info(String.format(
        Locale.US,
        "t='%d': syncRemoteToLocal END",
        logicalT));
  }

  /**
   * Attempts to synchronize the given remote change event into the local database.
   *
   * @param nsConfig          the namespace configuration.
   * @param docConfig         the document configuration related to the event.
   * @param remoteChangeEvent the remote change event to synchronize into the local database.
   */
  private void syncRemoteChangeEventToLocal(
      final NamespaceSynchronizationConfig nsConfig,
      final CoreDocumentSynchronizationConfig docConfig,
      final ChangeEvent<BsonDocument> remoteChangeEvent
  ) {
    if (docConfig.hasUncommittedWrites() && docConfig.getLastResolution() == logicalT) {
      logger.info(String.format(
          Locale.US,
          "t='%d': syncRemoteChangeEventToLocal have writes for %s but happened at same t; "
              + "waiting until next pass",
          logicalT,
          docConfig.getDocumentId()));
      return;
    }

    logger.info(String.format(
        Locale.US,
        "t='%d': syncRemoteChangeEventToLocal ns=%s documentId=%s processing operation='%s'",
        logicalT,
        nsConfig.getNamespace(),
        docConfig.getDocumentId(),
        remoteChangeEvent.getOperationType().toString()));

    final DocumentVersionInfo currentRemoteVersionInfo;
    try {
      currentRemoteVersionInfo = DocumentVersionInfo
          .getRemoteVersionInfo(remoteChangeEvent.getFullDocument());
    } catch (final Exception e) {
      desyncDocumentFromRemote(nsConfig.getNamespace(), docConfig.getDocumentId());
      emitError(docConfig,
          String.format(
              Locale.US,
              "t='%d': syncRemoteChangeEventToLocal ns=%s documentId=%s got a remote "
                  + "document that could not have its version info parsed "
                  + "; dropping the event, and desyncing the document",
              logicalT,
              nsConfig.getNamespace(),
              docConfig.getDocumentId()));
      return;
    }


    if (currentRemoteVersionInfo.hasVersion()
        && currentRemoteVersionInfo.getVersion().getSyncProtocolVersion() != 1) {
      desyncDocumentFromRemote(nsConfig.getNamespace(), docConfig.getDocumentId());

      emitError(docConfig,
              String.format(
                      Locale.US,
                      "t='%d': syncRemoteChangeEventToLocal ns=%s documentId=%s got a remote "
                              + "document with an unsupported synchronization protocol version "
                              + "%d; dropping the event, and desyncing the document",
                      logicalT,
                      nsConfig.getNamespace(),
                      docConfig.getDocumentId(),
                      currentRemoteVersionInfo.getVersion().getSyncProtocolVersion()));

      return;
    }

    // ii. If the version info for the unprocessed change event has the same GUID as the local
    //     document version GUID, and has a version counter less than or equal to the local
    //     document version version counter, drop the event, as it implies the event has already
    //     been applied to the local collection.
    if (docConfig.hasCommittedVersion(currentRemoteVersionInfo)) {
      // Skip this event since we generated it.
      logger.info(String.format(
          Locale.US,
          "t='%d': syncRemoteChangeEventToLocal ns=%s documentId=%s remote change event was "
              + "generated by us; dropping the event",
          logicalT,
          nsConfig.getNamespace(),
          docConfig.getDocumentId()));
      return;
    }


    // iii. If the document does not have local writes pending, apply the change event to the local
    //      document and emit a change event for it.
    if (docConfig.getLastUncommittedChangeEvent() == null) {
      switch (remoteChangeEvent.getOperationType()) {
        case REPLACE:
        case UPDATE:
        case INSERT:
          logger.info(String.format(
              Locale.US,
              "t='%d': syncRemoteChangeEventToLocal ns=%s documentId=%s replacing local with "
                  + "remote document with new version as there are no local pending writes: %s",
              logicalT,
              nsConfig.getNamespace(),
              docConfig.getDocumentId(),
              remoteChangeEvent.getFullDocument()));
          replaceOrUpsertOneFromRemote(
              nsConfig.getNamespace(),
              docConfig.getDocumentId(),
              remoteChangeEvent.getFullDocument(),
              DocumentVersionInfo.getDocumentVersionDoc(remoteChangeEvent.getFullDocument()));
          return;
        case DELETE:
          logger.info(String.format(
              Locale.US,
              "t='%d': syncRemoteChangeEventToLocal ns=%s documentId=%s deleting local as "
                  + "there are no local pending writes",
              logicalT,
              nsConfig.getNamespace(),
              docConfig.getDocumentId()));
          deleteOneFromRemote(
              nsConfig.getNamespace(),
              docConfig.getDocumentId());
          return;
        default:
          emitError(docConfig,
              String.format(
                  Locale.US,
                  "t='%d': syncRemoteChangeEventToLocal ns=%s documentId=%s unknown operation type "
                      + "occurred on the document: %s; dropping the event",
                  logicalT,
                  nsConfig.getNamespace(),
                  docConfig.getDocumentId(),
                  remoteChangeEvent.getOperationType().toString()));
          return;
      }
    }

    // At this point, we know there is a pending write for this document, so we will either drop
    // the event if we know it is already applied or we know the event is stale, or we will raise a
    // conflict.

    // iv. Otherwise, check if the version info of the incoming remote change event is different
    //     from the version of the local document.
    final DocumentVersionInfo lastKnownLocalVersionInfo = DocumentVersionInfo
          .getLocalVersionInfo(docConfig);

    // 1. If either the local document version or the remote change event version are empty, raise
    //    a conflict. The absence of a version is effectively a version, and a remote change event
    //    with no version indicates a document that may have been committed by another client not
    //    adhering to the mobile sync protocol.
    if (!lastKnownLocalVersionInfo.hasVersion() || !currentRemoteVersionInfo.hasVersion()) {
      logger.info(String.format(
              Locale.US,
              "t='%d': syncRemoteChangeEventToLocal ns=%s documentId=%s remote or local have an "
                      + "empty version but a write is pending; raising conflict",
              logicalT,
              nsConfig.getNamespace(),
              docConfig.getDocumentId()));
      resolveConflict(nsConfig.getNamespace(), docConfig, remoteChangeEvent);
      return;
    }

    // 2. Check if the GUID of the two versions are the same.
    final DocumentVersionInfo.Version localVersion = lastKnownLocalVersionInfo.getVersion();
    final DocumentVersionInfo.Version remoteVersion = currentRemoteVersionInfo.getVersion();
    if (localVersion.instanceId.equals(remoteVersion.instanceId)) {
      // a. If the GUIDs are the same, compare the version counter of the remote change event with
      //    the version counter of the local document
      if (remoteVersion.versionCounter <= localVersion.versionCounter) {
        // i. drop the event if the version counter of the remote event less than or equal to the
        // version counter of the local document
        logger.info(String.format(
                Locale.US,
                "t='%d': syncRemoteChangeEventToLocal ns=%s documentId=%s remote change event "
                        + "is stale; dropping the event",
                logicalT,
                nsConfig.getNamespace(),
                docConfig.getDocumentId()));
        return;
      } else {
        // ii. raise a conflict if the version counter of the remote event is greater than the
        //     version counter of the local document
        logger.info(String.format(
                Locale.US,
                "t='%d': syncRemoteChangeEventToLocal ns=%s documentId=%s remote event version "
                        + "has higher counter than local version but a write is pending; "
                        + "raising conflict",
                logicalT,
                nsConfig.getNamespace(),
                docConfig.getDocumentId()));
        resolveConflict(
                nsConfig.getNamespace(),
                docConfig,
                remoteChangeEvent);
        return;
      }
    }

    // b.  If the GUIDs are different, do a full document lookup against the remote server to
    //     fetch the latest version (this is to guard against the case where the unprocessed
    //     change event is stale).
    final BsonDocument newestRemoteDocument = this.getRemoteCollection(nsConfig.getNamespace())
            .find(new Document("_id", docConfig.getDocumentId())).first();

    if (newestRemoteDocument == null) {
      // i. If the document is not found with a remote lookup, this means the document was
      //    deleted remotely, so raise a conflict using a synthesized delete event as the remote
      //    change event.
      logger.info(String.format(
              Locale.US,
              "t='%d': syncRemoteChangeEventToLocal ns=%s documentId=%s remote event version "
                      + "stale and latest document lookup indicates a remote delete occurred, but "
                      + "a write is pending; raising conflict",
              logicalT,
              nsConfig.getNamespace(),
              docConfig.getDocumentId()));
      resolveConflict(
              nsConfig.getNamespace(),
              docConfig,
              changeEventForLocalDelete(
                      nsConfig.getNamespace(),
                      docConfig.getDocumentId(),
                      docConfig.hasUncommittedWrites()));
      return;
    }


    final DocumentVersionInfo newestRemoteVersionInfo;
    try {
      newestRemoteVersionInfo = DocumentVersionInfo
          .getRemoteVersionInfo(newestRemoteDocument);
    } catch (final Exception e) {
      desyncDocumentFromRemote(nsConfig.getNamespace(), docConfig.getDocumentId());
      emitError(docConfig,
          String.format(
              Locale.US,
              "t='%d': syncRemoteChangeEventToLocal ns=%s documentId=%s got a remote "
                  + "document that could not have its version info parsed "
                  + "; dropping the event, and desyncing the document",
              logicalT,
              nsConfig.getNamespace(),
              docConfig.getDocumentId()));
      return;
    }

    // ii. If the current GUID of the remote document (as determined by this lookup) is equal
    //     to the GUID of the local document, drop the event. We're believed to be behind in
    //     the change stream at this point.
    if (newestRemoteVersionInfo.hasVersion()
            && newestRemoteVersionInfo.getVersion().getInstanceId()
                    .equals(localVersion.instanceId)) {

      logger.info(String.format(
              Locale.US,
              "t='%d': syncRemoteChangeEventToLocal ns=%s documentId=%s latest document lookup "
                      + "indicates that this is a stale event; dropping the event",
              logicalT,
              nsConfig.getNamespace(),
              docConfig.getDocumentId()));
      return;

    }

    // iii. If the current GUID of the remote document is not equal to the GUID of the local
    //      document, raise a conflict using a synthesized replace event as the remote change
    //      event. This means the remote document is a legitimately new document and we should
    //      handle the conflict.
    logger.info(String.format(
            Locale.US,
            "t='%d': syncRemoteChangeEventToLocal ns=%s documentId=%s latest document lookup "
                    + "indicates a remote replace occurred, but a local write is pending; raising "
                    + "conflict with synthesized replace event",
            logicalT,
            nsConfig.getNamespace(),
            docConfig.getDocumentId()));
    resolveConflict(
            nsConfig.getNamespace(),
            docConfig,
            changeEventForLocalReplace(
                    nsConfig.getNamespace(),
                    docConfig.getDocumentId(),
                    newestRemoteDocument,
                    docConfig.hasUncommittedWrites()));
  }

  /**
   * Synchronizes the local state of every requested document to be synchronized with the remote
   * state of said documents. Any conflicts that occur will be resolved locally and later relayed
   * remotely on a subsequent iteration of {@link DataSynchronizer#doSyncPass()}.
   */
  private void syncLocalToRemote() {
    logger.info(String.format(
        Locale.US,
        "t='%d': syncLocalToRemote START",
        logicalT));

    // 1. Run local to remote (L2R) sync routine
    // Search for modifications in each namespace.
    for (final NamespaceSynchronizationConfig nsConfig : syncConfig) {
      nsConfig.getLock().writeLock().lock();
      try {
        final CoreRemoteMongoCollection<BsonDocument> remoteColl =
            getRemoteCollection(nsConfig.getNamespace());

        // a. For each document that has local writes pending
        for (final CoreDocumentSynchronizationConfig docConfig : nsConfig) {
          if (!docConfig.hasUncommittedWrites() || docConfig.isPaused()) {
            continue;
          }
          if (docConfig.getLastResolution() == logicalT) {
            logger.info(String.format(
                Locale.US,
                "t='%d': syncLocalToRemote ns=%s documentId=%s has writes from current logicalT; "
                    + "waiting until next pass",
                logicalT,
                nsConfig.getNamespace(),
                docConfig.getDocumentId()));
            continue;
          }

          // i. Retrieve the change event for this local document in the local config metadata
          final ChangeEvent<BsonDocument> localChangeEvent =
              docConfig.getLastUncommittedChangeEvent();
          logger.info(String.format(
              Locale.US,
              "t='%d': syncLocalToRemote ns=%s documentId=%s processing operation='%s'",
              logicalT,
              nsConfig.getNamespace(),
              docConfig.getDocumentId(),
              localChangeEvent.getOperationType().toString()));

          final BsonDocument localDoc = localChangeEvent.getFullDocument();
          final BsonDocument docFilter = getDocumentIdFilter(docConfig.getDocumentId());

          boolean isConflicted = false;

          // This is here as an optimization in case an op requires we look up the remote document
          // in advance and we only want to do this once.
          BsonDocument remoteDocument = null;
          boolean remoteDocumentFetched = false;

          final DocumentVersionInfo localVersionInfo =
              DocumentVersionInfo.getLocalVersionInfo(docConfig);
          final BsonDocument nextVersion;

          // ii. Check if the internal remote change stream listener has an unprocessed event for
          //     this document.
          final ChangeEvent<BsonDocument> unprocessedRemoteEvent =
              instanceChangeStreamListener.getUnprocessedEventForDocumentId(
                  nsConfig.getNamespace(),
                  docConfig.getDocumentId());

          if (unprocessedRemoteEvent != null) {
            final DocumentVersionInfo unprocessedEventVersion;
            try {
              unprocessedEventVersion = DocumentVersionInfo
                  .getRemoteVersionInfo(unprocessedRemoteEvent.getFullDocument());
            } catch (final Exception e) {
              desyncDocumentFromRemote(nsConfig.getNamespace(), docConfig.getDocumentId());
              emitError(docConfig,
                  String.format(
                      Locale.US,
                      "t='%d': syncLocalToRemote ns=%s documentId=%s got a remote "
                          + "document that could not have its version info parsed "
                          + "; dropping the event, and desyncing the document",
                      logicalT,
                      nsConfig.getNamespace(),
                      docConfig.getDocumentId()));
              continue;
            }

            // 1. If it does and the version info is different, record that a conflict has occurred.
            //    Difference is determined if either the GUID is different or the version counter is
            //    greater than the local version counter, or if both versions are empty.
            if (!docConfig.hasCommittedVersion(unprocessedEventVersion)) {
              isConflicted = true;
              logger.info(String.format(
                  Locale.US,
                  "t='%d': syncLocalToRemote ns=%s documentId=%s version different on "
                      + "unprocessed change event for document; raising conflict",
                  logicalT,
                  nsConfig.getNamespace(),
                  docConfig.getDocumentId()));
            }

            // 2. Otherwise, the unprocessed event can be safely dropped and ignored in future R2L
            //    passes. Continue on to checking the operation type.
          }

          if (!isConflicted) {
            // iii. Check the operation type
            switch (localChangeEvent.getOperationType()) {
              // 1. INSERT
              case INSERT: {
                nextVersion = DocumentVersionInfo.getFreshVersionDocument();

                // It's possible that we may insert after a delete happened and we didn't get a
                // notification for it. There's nothing we can do about this.

                // a. Insert document into remote database
                try {
                  remoteColl.insertOne(
                      withNewVersion(localChangeEvent.getFullDocument(), nextVersion));
                } catch (final StitchServiceException ex) {
                  // b. If an error happens:

                  // i. That is not a duplicate key exception, report an error to the error
                  // listener.
                  if (ex.getErrorCode() != StitchServiceErrorCode.MONGODB_ERROR
                      || !ex.getMessage().contains("E11000")) {
                    this.emitError(docConfig, String.format(
                        Locale.US,
                        "t='%d': syncLocalToRemote ns=%s documentId=%s exception inserting: %s",
                        logicalT,
                        nsConfig.getNamespace(),
                        docConfig.getDocumentId(),
                        ex), ex);
                    continue;
                  }

                  // ii. Otherwise record that a conflict has occurred.
                  logger.info(String.format(
                      Locale.US,
                      "t='%d': syncLocalToRemote ns=%s documentId=%s duplicate key exception on "
                          + "insert; raising conflict",
                      logicalT,
                      nsConfig.getNamespace(),
                      docConfig.getDocumentId()));
                  isConflicted = true;
                }
                break;
              }


              // 2. REPLACE
              case REPLACE: {
                if (localDoc == null) {
                  final IllegalStateException illegalStateException = new IllegalStateException(
                      "expected document to exist for local replace change event: %s");

                  emitError(
                      docConfig,
                      illegalStateException.getMessage(),
                      illegalStateException
                  );
                  continue;
                }
                nextVersion = localVersionInfo.getNextVersion();
                final BsonDocument nextDoc = withNewVersion(localDoc, nextVersion);

                // a. Update the document in the remote database using a query for the _id and the
                //    version with an update containing the replacement document with the version
                //    counter incremented by 1.
                final RemoteUpdateResult result;
                try {
                  result = remoteColl.updateOne(
                      localVersionInfo.getFilter(),
                      nextDoc);
                } catch (final StitchServiceException ex) {
                  // b. If an error happens, report an error to the error listener.
                  this.emitError(
                      docConfig,
                      String.format(
                          Locale.US,
                          "t='%d': syncLocalToRemote ns=%s documentId=%s exception "
                              + "replacing: %s",
                          logicalT,
                          nsConfig.getNamespace(),
                          docConfig.getDocumentId(),
                          ex),
                      ex
                  );
                  continue;
                }
                // c. If no documents are matched, record that a conflict has occurred.
                if (result.getMatchedCount() == 0) {
                  isConflicted = true;
                  logger.info(String.format(
                      Locale.US,
                      "t='%d': syncLocalToRemote ns=%s documentId=%s version different on "
                          + "replaced document or document deleted; raising conflict",
                      logicalT,
                      nsConfig.getNamespace(),
                      docConfig.getDocumentId()));
                }
                break;
              }

              // 3. UPDATE
              case UPDATE: {
                if (localDoc == null) {
                  final IllegalStateException illegalStateException = new IllegalStateException(
                      "expected document to exist for local update change event");
                  emitError(
                      docConfig,
                      illegalStateException.getMessage(),
                      illegalStateException
                  );
                  continue;
                }

                final ChangeEvent.UpdateDescription localUpdateDescription =
                        localChangeEvent.getUpdateDescription();
                if (localUpdateDescription.getRemovedFields().isEmpty()
                        && localUpdateDescription.getUpdatedFields().isEmpty()) {
                  // if the translated update is empty, then this update is a noop, and we
                  // shouldn't update because it would improperly update the version information.
                  logger.info(String.format(
                          Locale.US,
                          "t='%d': syncLocalToRemote ns=%s documentId=%s local change event "
                                  + "update description is empty for UPDATE; dropping the event",
                          logicalT,
                          nsConfig.getNamespace(),
                          docConfig.getDocumentId()));
                  continue;
                }


                // a. Update the document in the remote database using a query for the _id and the
                //    version with an update containing the replacement document with the version
                //    counter incremented by 1.

                // create an update document from the local change event's update description, and
                // set the version of the new document to the next logical version
                nextVersion = localVersionInfo.getNextVersion();

                final BsonDocument translatedUpdate = new BsonDocument();
                final BsonDocument sets = new BsonDocument();
                final BsonDocument unsets = new BsonDocument();

                if (!localUpdateDescription.getUpdatedFields().isEmpty()) {
                  for (final Map.Entry<String, BsonValue> fieldValue :
                      localUpdateDescription.getUpdatedFields().entrySet()) {
                    sets.put(fieldValue.getKey(), fieldValue.getValue());
                  }
                }

                if (!localUpdateDescription.getRemovedFields().isEmpty()) {
                  for (final String field :
                      localUpdateDescription.getRemovedFields()) {
                    unsets.put(field, BsonBoolean.TRUE);
                  }
                  translatedUpdate.put("$unset", unsets);
                }

                sets.put(DOCUMENT_VERSION_FIELD, nextVersion);
                translatedUpdate.put("$set", sets);

                final RemoteUpdateResult result;
                try {
                  result = remoteColl.updateOne(
                      localVersionInfo.getFilter(),
                      translatedUpdate
                  );
                } catch (final StitchServiceException ex) {
                  // b. If an error happens, report an error to the error listener.
                  emitError(
                      docConfig,
                      String.format(
                          Locale.US,
                          "t='%d': syncLocalToRemote ns=%s documentId=%s exception "
                              + "updating: %s",
                          logicalT,
                          nsConfig.getNamespace(),
                          docConfig.getDocumentId(),
                          ex),
                      ex
                  );
                  continue;
                }
                if (result.getMatchedCount() == 0) {
                  // c. If no documents are matched, record that a conflict has occurred.
                  isConflicted = true;
                  logger.info(String.format(
                      Locale.US,
                      "t='%d': syncLocalToRemote ns=%s documentId=%s version different on "
                          + "updated document or document deleted; raising conflict",
                      logicalT,
                      nsConfig.getNamespace(),
                      docConfig.getDocumentId()));
                }
                break;
              }

              case DELETE: {
                nextVersion = null;
                final RemoteDeleteResult result;
                // a. Delete the document in the remote database using a query for the _id and the
                //    version.
                try {
                  result = remoteColl.deleteOne(localVersionInfo.getFilter());
                } catch (final StitchServiceException ex) {
                  // b. If an error happens, report an error to the error listener.
                  emitError(
                      docConfig,
                      String.format(
                          Locale.US,
                          "t='%d': syncLocalToRemote ns=%s documentId=%s exception "
                              + " deleting: %s",
                          logicalT,
                          nsConfig.getNamespace(),
                          docConfig.getDocumentId(),
                          ex),
                      ex
                  );
                  continue;
                }
                // c. If no documents are matched, record that a conflict has occurred.
                if (result.getDeletedCount() == 0) {
                  remoteDocument = remoteColl.find(docFilter).first();
                  remoteDocumentFetched = true;
                  if (remoteDocument != null) {
                    isConflicted = true;
                    logger.info(String.format(
                        Locale.US,
                        "t='%d': syncLocalToRemote ns=%s documentId=%s version different on "
                            + "removed document; raising conflict",
                        logicalT,
                        nsConfig.getNamespace(),
                        docConfig.getDocumentId()));
                  } else {
                    // d. Desynchronize the document if there is no conflict, or if fetching a
                    // remote document after the conflict is raised returns no remote document.
                    desyncDocumentFromRemote(nsConfig.getNamespace(), docConfig.getDocumentId());
                  }
                } else {
                  desyncDocumentFromRemote(nsConfig.getNamespace(), docConfig.getDocumentId());
                }
                break;
              }

              default:
                emitError(
                    docConfig,
                    String.format(
                        Locale.US,
                        "t='%d': syncLocalToRemote ns=%s documentId=%s unknown operation "
                            + "type occurred on the document: %s; dropping the event",
                        logicalT,
                        nsConfig.getNamespace(),
                        docConfig.getDocumentId(),
                        localChangeEvent.getOperationType().toString())
                );
                continue;
            }
          } else {
            nextVersion = null;
          }

          logger.info(String.format(
              Locale.US,
              "t='%d': syncLocalToRemote ns=%s documentId=%s conflict=%s",
              logicalT,
              nsConfig.getNamespace(),
              docConfig.getDocumentId(),
              isConflicted));

          if (!isConflicted) {
            // iv. If no conflict has occurred, move on to the remote to local sync routine.

            // since we strip version information from documents before setting pending writes, we
            // don't have to worry about a stale document version in the event here.
            final ChangeEvent<BsonDocument> committedEvent =
                docConfig.getLastUncommittedChangeEvent();
            emitEvent(docConfig.getDocumentId(), new ChangeEvent<>(
                committedEvent.getId(),
                committedEvent.getOperationType(),
                committedEvent.getFullDocument(),
                committedEvent.getNamespace(),
                committedEvent.getDocumentKey(),
                committedEvent.getUpdateDescription(),
                false));

            docConfig.setPendingWritesComplete(nextVersion);
          } else {
            // v. Otherwise, invoke the collection-level conflict handler with the local change
            // event and the remote change event (synthesized by doing a lookup of the document or
            // sourced from the listener)
            final ChangeEvent<BsonDocument> remoteChangeEvent;
            if (!remoteDocumentFetched) {
              remoteChangeEvent =
                  getSynthesizedRemoteChangeEventForDocument(remoteColl, docConfig.getDocumentId());
            } else {
              remoteChangeEvent =
                  getSynthesizedRemoteChangeEventForDocument(
                      remoteColl.getNamespace(),
                      docConfig.getDocumentId(),
                      remoteDocument);
            }
            resolveConflict(
                nsConfig.getNamespace(),
                docConfig,
                remoteChangeEvent);
          }
        }
      } finally {
        nsConfig.getLock().writeLock().unlock();
      }
    }

    logger.info(String.format(
        Locale.US,
        "t='%d': syncLocalToRemote END",
        logicalT));

    // 3. If there are still local writes pending for the document, it will go through the L2R
    //    phase on a subsequent pass and try to commit changes again.
  }

  private void emitError(final CoreDocumentSynchronizationConfig docConfig,
                         final String msg) {
    this.emitError(docConfig, msg, null);
  }

  private void emitError(final CoreDocumentSynchronizationConfig docConfig,
                         final String msg,
                         final Exception ex) {
    if (this.errorListener != null) {
      final Exception dispatchException;
      if (ex == null) {
        dispatchException = new DataSynchronizerException(msg);
      } else {
        dispatchException = ex;
      }
      this.eventDispatcher.dispatch(() -> {
        errorListener.onError(docConfig.getDocumentId(), dispatchException);
        return null;
      });
    }

    docConfig.setPaused(true);

    this.logger.error(msg);
    this.logger.error(
            String.format("Setting document %s to frozen", docConfig.getDocumentId()));
  }


  /**
   * Resolves a conflict between a synchronized document's local and remote state. The resolution
   * will result in either the document being desynchronized or being replaced with some resolved
   * state based on the conflict resolver specified for the document.
   *
   * @param namespace   the namespace where the document lives.
   * @param docConfig   the configuration of the document that describes the resolver and current
   *                    state.
   * @param remoteEvent the remote change event that is conflicting.
   */
  private void resolveConflict(
      final MongoNamespace namespace,
      final CoreDocumentSynchronizationConfig docConfig,
      final ChangeEvent<BsonDocument> remoteEvent
  ) {
    if (this.syncConfig.getNamespaceConfig(namespace).getConflictHandler() == null) {
      logger.warn(String.format(
          Locale.US,
          "t='%d': resolveConflict ns=%s documentId=%s no conflict resolver set; cannot "
                  + "resolve yet",
          logicalT,
          namespace,
          docConfig.getDocumentId()));
      return;
    }

    logger.info(String.format(
        Locale.US,
        "t='%d': resolveConflict ns=%s documentId=%s resolving conflict between localOp=%s "
            + "remoteOp=%s",
        logicalT,
        namespace,
        docConfig.getDocumentId(),
        docConfig.getLastUncommittedChangeEvent().getOperationType(),
        remoteEvent.getOperationType()));

    // 2. Based on the result of the handler determine the next state of the document.
    final Object resolvedDocument;
    final ChangeEvent transformedRemoteEvent;
    try {
      final ChangeEvent transformedLocalEvent = ChangeEvent.transformChangeEventForUser(
          docConfig.getLastUncommittedChangeEvent(),
          syncConfig.getNamespaceConfig(namespace).getDocumentCodec());
      transformedRemoteEvent =
          ChangeEvent.transformChangeEventForUser(
              remoteEvent,
              syncConfig.getNamespaceConfig(namespace).getDocumentCodec());
      resolvedDocument = resolveConflictWithResolver(
          this.syncConfig.getNamespaceConfig(namespace).getConflictHandler(),
          docConfig.getDocumentId(),
          transformedLocalEvent,
          transformedRemoteEvent);
    } catch (final Exception ex) {
      logger.error(String.format(
          Locale.US,
          "t='%d': resolveConflict ns=%s documentId=%s resolution exception: %s",
          logicalT,
          namespace,
          docConfig.getDocumentId(),
          ex));
      emitError(docConfig,
          String.format(
              Locale.US,
              "t='%d': resolveConflict ns=%s documentId=%s resolution exception: %s",
              logicalT,
              namespace,
              docConfig.getDocumentId(),
              ex),
          ex);
      return;
    }

    final BsonDocument remoteVersion;
    if (remoteEvent.getOperationType() == ChangeEvent.OperationType.DELETE) {
      // We expect there will be no version on the document. Note: it's very possible
      // that the document could be reinserted at this point with no version field and we
      // would end up deleting it, unless we receive a notification in time.
      remoteVersion = null;
    } else {
      try {
        final DocumentVersionInfo remoteVersionInfo = DocumentVersionInfo
            .getRemoteVersionInfo(remoteEvent.getFullDocument());
        remoteVersion = remoteVersionInfo.getVersionDoc();
      } catch (final Exception e) {
        desyncDocumentFromRemote(namespace, docConfig.getDocumentId());
        emitError(docConfig,
            String.format(
                Locale.US,
                "t='%d': resolveConflict ns=%s documentId=%s got a remote "
                    + "document that could not have its version info parsed "
                    + "; dropping the event, and desyncing the document",
                logicalT,
                namespace,
                docConfig.getDocumentId()));
        return;
      }
    }

    final boolean acceptRemote =
        (remoteEvent.getFullDocument() == null && resolvedDocument == null)
            || (remoteEvent.getFullDocument() != null
            && transformedRemoteEvent.getFullDocument().equals(resolvedDocument));

    // a. If the resolved document is null:
    if (resolvedDocument == null) {
      logger.info(String.format(
          Locale.US,
          "t='%d': resolveConflict ns=%s documentId=%s deleting local and remote with remote "
              + "version acknowledged",
          logicalT,
          namespace,
          docConfig.getDocumentId()));

      if (acceptRemote) {
        // i. If the remote event was a DELETE, delete the document locally, desynchronize the
        //    document, and emit a change event for the deletion.
        deleteOneFromRemote(namespace, docConfig.getDocumentId());
      } else {
        // ii. Otherwise, delete the document locally, mark that there are pending writes for this
        //     document, and emit a change event for the deletion.
        deleteOneFromResolution(namespace, docConfig.getDocumentId(), remoteVersion);
      }
    } else {
      // b. If the resolved document is not null:

      // Update the document locally which will keep the pending writes but with
      // a new version next time around.
      @SuppressWarnings("unchecked") final BsonDocument docForStorage =
          BsonUtils.documentToBsonDocument(
              resolvedDocument,
              syncConfig.getNamespaceConfig(namespace).getDocumentCodec());

      logger.info(String.format(
          Locale.US,
          "t='%d': resolveConflict ns=%s documentId=%s replacing local with resolved document "
                  + "with remote version acknowledged: %s",
          logicalT,
          namespace,
          docConfig.getDocumentId(),
          docForStorage.toJson()));
      if (acceptRemote) {
        // i. If the remote document is equal to the resolved document, replace the document
        //    locally, mark the document as having no pending writes, and emit a REPLACE change
        //    event if the document had not existed prior, or UPDATE if it had.
        replaceOrUpsertOneFromRemote(
            namespace,
            docConfig.getDocumentId(),
            docForStorage,
            remoteVersion);
      } else {
        // ii. Otherwise, replace the local document with the resolved document locally, mark that
        //     there are pending writes for this document, and emit an UPDATE change event, or a
        //     DELETE change event (if the remoteEvent's operation type was DELETE).
        updateOrUpsertOneFromResolution(
            namespace,
            docConfig.getDocumentId(),
            docForStorage,
            remoteVersion,
            remoteEvent);
      }
    }
  }

  /**
   * Returns the resolution of resolving the conflict between a local and remote event using
   * the given conflict resolver.
   *
   * @param conflictResolver the conflict resolver to use.
   * @param documentId       the document id related to the conflicted events.
   * @param localEvent       the conflicted local event.
   * @param remoteEvent      the conflicted remote event.
   * @return the resolution to the conflict.
   */
  @SuppressWarnings("unchecked")
  private static Object resolveConflictWithResolver(
      final ConflictHandler conflictResolver,
      final BsonValue documentId,
      final ChangeEvent localEvent,
      final ChangeEvent remoteEvent
  ) {
    return conflictResolver.resolveConflict(
        documentId,
        localEvent,
        remoteEvent);
  }

  /**
   * Returns a synthesized change event for a remote document.
   *
   * @param remoteColl the collection the document lives in.
   * @param documentId the _id of the document.
   * @return a synthesized change event for a remote document.
   */
  private ChangeEvent<BsonDocument> getSynthesizedRemoteChangeEventForDocument(
      final CoreRemoteMongoCollection<BsonDocument> remoteColl,
      final BsonValue documentId
  ) {
    return getSynthesizedRemoteChangeEventForDocument(
        remoteColl.getNamespace(),
        documentId,
        remoteColl.find(getDocumentIdFilter(documentId)).first());
  }

  /**
   * Returns a synthesized change event for a remote document.
   *
   * @param ns         the namspace where the document lives.
   * @param documentId the _id of the document.
   * @param document   the remote document.
   * @return a synthesized change event for a remote document.
   */
  private ChangeEvent<BsonDocument> getSynthesizedRemoteChangeEventForDocument(
      final MongoNamespace ns,
      final BsonValue documentId,
      final BsonDocument document
  ) {
    // a. When the document is looked up, if it cannot be found the synthesized change event is a
    // DELETE, otherwise it's a REPLACE.
    if (document == null) {
      return changeEventForLocalDelete(ns, documentId, false);
    }
    return changeEventForLocalReplace(ns, documentId, document, false);
  }

  /**
   * Queues up a callback to be removed and invoked on the next change event.
   */
  public void addWatcher(final MongoNamespace namespace,
                                     final Callback<ChangeEvent<BsonDocument>, Object> watcher) {
    instanceChangeStreamListener.addWatcher(namespace, watcher);
  }

  public void removeWatcher(final MongoNamespace namespace,
                          final Callback<ChangeEvent<BsonDocument>, Object> watcher) {
    instanceChangeStreamListener.removeWatcher(namespace, watcher);
  }

  Map<BsonValue, ChangeEvent<BsonDocument>> getEventsForNamespace(final MongoNamespace namespace) {
    return instanceChangeStreamListener.getEventsForNamespace(namespace);
  }

  // ----- CRUD operations -----

  /**
   * Returns the set of synchronized namespaces.
   *
   * @return the set of synchronized namespaces.
   */
  public Set<MongoNamespace> getSynchronizedNamespaces() {
    return this.syncConfig.getSynchronizedNamespaces();
  }

  /**
   * Returns the set of synchronized documents in a namespace.
   *
   * @param namespace the namespace to get synchronized documents for.
   * @return the set of synchronized documents in a namespace.
   */
  public Set<CoreDocumentSynchronizationConfig> getSynchronizedDocuments(
      final MongoNamespace namespace
  ) {
    return this.syncConfig.getSynchronizedDocuments(namespace);
  }

  /**
   * Returns the set of synchronized documents _ids in a namespace.
   *
   * @param namespace the namespace to get synchronized documents _ids for.
   * @return the set of synchronized documents _ids in a namespace.
   */
  public Set<BsonValue> getSynchronizedDocumentIds(final MongoNamespace namespace) {
    return this.syncConfig.getSynchronizedDocumentIds(namespace);
  }

  /**
   * Return the set of synchronized document _ids in a namespace
   * that have been paused due to an irrecoverable error.
   *
   * @param namespace the namespace to get paused document _ids for.
   * @return the set of paused document _ids in a namespace
   */
  public Set<BsonValue> getPausedDocumentIds(final MongoNamespace namespace) {
    final Set<BsonValue> pausedDocumentIds = new HashSet<>();

    for (final CoreDocumentSynchronizationConfig config :
        this.syncConfig.getSynchronizedDocuments(namespace)) {
      if (config.isPaused()) {
        pausedDocumentIds.add(config.getDocumentId());
      }
    }

    return pausedDocumentIds;
  }

  /**
   * Requests that a document be synchronized by the given _id. Actual synchronization of the
   * document will happen later in a {@link DataSynchronizer#doSyncPass()} iteration.
   *
   * @param namespace  the namespace to put the document in.
   * @param documentId the _id of the document.
   */
  public void syncDocumentFromRemote(
      final MongoNamespace namespace,
      final BsonValue documentId
  ) {
    syncConfig.addSynchronizedDocument(namespace, documentId);
    triggerListeningToNamespace(namespace);
  }

  /**
   * Requests that a document be no longer be synchronized by the given _id. Any uncommitted writes
   * will be lost.
   *
   * @param namespace  the namespace to put the document in.
   * @param documentId the _id of the document.
   */
  public void desyncDocumentFromRemote(
      final MongoNamespace namespace,
      final BsonValue documentId
  ) {
    final Lock lock =
        this.syncConfig.getNamespaceConfig(namespace).getLock().writeLock();
    lock.lock();
    try {
      syncConfig.removeSynchronizedDocument(namespace, documentId);
      getLocalCollection(namespace).deleteOne(getDocumentIdFilter(documentId));
    } finally {
      lock.unlock();
    }
    triggerListeningToNamespace(namespace);
  }

  /**
   * A document that is paused no longer has remote updates applied to it.
   * Any local updates to this document cause it to be resumed. An example of pausing a document
   * is when a conflict is being resolved for that document and the handler throws an exception.
   *
   * This method allows you to resume sync for a document.
   *
   * @param namespace  namespace for the document
   * @param documentId the id of the document to resume syncing
   * @return true if successfully resumed, false if the document
   * could not be found or there was an error resuming
   */
  boolean resumeSyncForDocument(
      final MongoNamespace namespace,
      final BsonValue documentId
  ) {
    if (namespace == null || documentId == null) {
      return false;
    }

    final NamespaceSynchronizationConfig namespaceSynchronizationConfig;
    final CoreDocumentSynchronizationConfig config;

    if ((namespaceSynchronizationConfig = syncConfig.getNamespaceConfig(namespace)) == null
        || (config = namespaceSynchronizationConfig.getSynchronizedDocument(documentId)) == null) {
      return false;
    }

    config.setPaused(false);
    return !config.isPaused();
  }

  /**
   * Counts the number of documents in the collection.
   *
   * @return the number of documents in the collection
   */
  long count(final MongoNamespace namespace) {
    return count(namespace, new BsonDocument());
  }

  /**
   * Counts the number of documents in the collection according to the given options.
   *
   * @param filter the query filter
   * @return the number of documents in the collection
   */
  long count(final MongoNamespace namespace, final Bson filter) {
    return count(namespace, filter, new CountOptions());
  }

  /**
   * Counts the number of documents in the collection according to the given options.
   *
   * @param filter  the query filter
   * @param options the options describing the count
   * @return the number of documents in the collection
   */
  long count(final MongoNamespace namespace, final Bson filter, final CountOptions options) {
    final Lock lock =
        this.syncConfig.getNamespaceConfig(namespace).getLock().writeLock();
    lock.lock();
    try {
      return getLocalCollection(namespace).countDocuments(filter, options);
    } finally {
      lock.unlock();
    }
  }

  Collection<BsonDocument> find(
      final MongoNamespace namespace,
      final BsonDocument filter
  ) {
    final Lock lock =
        this.syncConfig.getNamespaceConfig(namespace).getLock().writeLock();
    lock.lock();
    try {
      return getLocalCollection(namespace)
          .find(filter)
          .into(new ArrayList<>());
    } finally {
      lock.unlock();
    }
  }

  public <T> Collection<T> find(
      final MongoNamespace namespace,
      final BsonDocument filter,
      final int limit,
      final BsonDocument projection,
      final BsonDocument sort,
      final Class<T> resultClass,
      final CodecRegistry codecRegistry
  ) {
    final Lock lock =
        this.syncConfig.getNamespaceConfig(namespace).getLock().writeLock();
    lock.lock();
    try {
      return getLocalCollection(namespace, resultClass, codecRegistry)
          .find(filter)
          .limit(limit)
          .projection(projection)
          .sort(sort)
          .into(new ArrayList<>());
    } finally {
      lock.unlock();
    }
  }

  /**
   * Aggregates documents according to the specified aggregation pipeline.
   *
   * @param pipeline the aggregation pipeline
   * @return an iterable containing the result of the aggregation operation
   */
  AggregateIterable<BsonDocument> aggregate(
      final MongoNamespace namespace,
      final List<? extends Bson> pipeline) {
    return aggregate(namespace, pipeline, BsonDocument.class);
  }

  /**
   * Aggregates documents according to the specified aggregation pipeline.
   *
   * @param pipeline    the aggregation pipeline
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return an iterable containing the result of the aggregation operation
   */
  <ResultT> AggregateIterable<ResultT> aggregate(
      final MongoNamespace namespace,
      final List<? extends Bson> pipeline,
      final Class<ResultT> resultClass) {
    final Lock lock =
        this.syncConfig.getNamespaceConfig(namespace).getLock().writeLock();
    lock.lock();
    try {
      return getLocalCollection(namespace).aggregate(pipeline, resultClass);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Inserts a single document locally and being to synchronize it based on its _id. Inserting
   * a document with the same _id twice will result in a duplicate key exception.
   *
   * @param namespace the namespace to put the document in.
   * @param document  the document to insert.
   */
  void insertOne(final MongoNamespace namespace, final BsonDocument document) {
    // Remove forbidden fields from the document before inserting it into the local collection.
    final BsonDocument docForStorage = sanitizeDocument(document);

    final Lock lock = this.syncConfig.getNamespaceConfig(namespace).getLock().writeLock();
    lock.lock();
    final ChangeEvent<BsonDocument> event;
    final BsonValue documentId;
    try {
      getLocalCollection(namespace).insertOne(docForStorage);
      documentId = BsonUtils.getDocumentId(docForStorage);
      event = changeEventForLocalInsert(namespace, docForStorage, true);
      final CoreDocumentSynchronizationConfig config = syncConfig.addSynchronizedDocument(
          namespace,
          documentId
      );
      config.setSomePendingWrites(logicalT, event);
    } finally {
      lock.unlock();
    }
    triggerListeningToNamespace(namespace);
    emitEvent(documentId, event);
  }

  /**
   * Inserts one or more documents.
   *
   * @param documents the documents to insert
   */
  void insertMany(final MongoNamespace namespace,
                  final List<BsonDocument> documents) {
    // Remove forbidden fields from the documents before inserting them into the local collection.
    final List<BsonDocument> docsForStorage = new ArrayList<>(documents.size());

    for (final BsonDocument document : documents) {
      docsForStorage.add(sanitizeDocument(document));
    }

    final Lock lock =
        this.syncConfig.getNamespaceConfig(namespace).getLock().writeLock();
    lock.lock();
    final List<ChangeEvent<BsonDocument>> eventsToEmit = new ArrayList<>();
    try {
      getLocalCollection(namespace).insertMany(docsForStorage);
      for (final BsonDocument document : docsForStorage) {
        final BsonValue documentId = BsonUtils.getDocumentId(document);
        final ChangeEvent<BsonDocument> event =
            changeEventForLocalInsert(namespace, document, true);
        final CoreDocumentSynchronizationConfig config = syncConfig.addSynchronizedDocument(
            namespace,
            documentId
        );
        config.setSomePendingWrites(logicalT, event);
        eventsToEmit.add(event);
      }
    } finally {
      lock.unlock();
    }
    triggerListeningToNamespace(namespace);
    for (final ChangeEvent<BsonDocument> event : eventsToEmit) {
      emitEvent(BsonUtils.getDocumentId(event.getDocumentKey()), event);
    }
  }

  /**
   * Update a single document in the collection according to the specified arguments.
   *
   * @param filter a document describing the query filter, which may not be null.
   * @param update a document describing the update, which may not be null. The update to
   *               apply must include only update operators.
   * @return the result of the update one operation
   */
  UpdateResult updateOne(final MongoNamespace namespace, final Bson filter, final Bson update) {
    return updateOne(namespace, filter, update, new UpdateOptions());
  }

  /**
   * Update a single document in the collection according to the specified arguments.
   *
   * @param filter        a document describing the query filter, which may not be null.
   * @param update        a document describing the update, which may not be null. The update to
   *                      apply must include only update operators.
   * @param updateOptions the options to apply to the update operation
   * @return the result of the update one operation
   */
  UpdateResult updateOne(
      final MongoNamespace namespace,
      final Bson filter,
      final Bson update,
      final UpdateOptions updateOptions) {
    final Lock lock =
        this.syncConfig.getNamespaceConfig(namespace).getLock().writeLock();
    lock.lock();
    final BsonValue documentId;
    final ChangeEvent<BsonDocument> event;
    final boolean triggerNamespace;
    try {
      // read the local collection
      final MongoCollection<BsonDocument> localCollection = getLocalCollection(namespace);
      final MongoCollection<BsonDocument> undoCollection = getUndoCollection(namespace);

      // fetch the document prior to updating
      final BsonDocument documentBeforeUpdate = localCollection.find(filter).first();

      // if there was no document prior and this is not an upsert,
      // do not acknowledge the update
      if (!updateOptions.isUpsert() && documentBeforeUpdate == null) {
        return UpdateResult.acknowledged(0, 0L, null);
      }

      if (documentBeforeUpdate != null) {
        undoCollection.insertOne(documentBeforeUpdate);
      }

      // find and update the single document, returning the document post-update
      final BsonDocument unsanitizedDocumentAfterUpdate = localCollection.findOneAndUpdate(
          filter,
          update,
          new FindOneAndUpdateOptions()
              .collation(updateOptions.getCollation())
              .upsert(updateOptions.isUpsert())
              .bypassDocumentValidation(updateOptions.getBypassDocumentValidation())
              .arrayFilters(updateOptions.getArrayFilters())
              .returnDocument(ReturnDocument.AFTER));

      // if the document was deleted between our earlier check and now, it will not have
      // been updated. do not acknowledge the update
      if (unsanitizedDocumentAfterUpdate == null) {
        if (documentBeforeUpdate != null) {
          undoCollection
              .deleteOne(getDocumentIdFilter(BsonUtils.getDocumentId(documentBeforeUpdate)));
        }
        return UpdateResult.acknowledged(0, 0L, null);
      }

      final CoreDocumentSynchronizationConfig config;
      documentId = BsonUtils.getDocumentId(unsanitizedDocumentAfterUpdate);

      // Ensure that the update didn't add any forbidden fields to the document, and remove them if
      // it did.
      final BsonDocument documentAfterUpdate =
              sanitizeCachedDocument(localCollection, unsanitizedDocumentAfterUpdate, documentId);

      // if there was no document prior and this was an upsert,
      // treat this as an insert.
      // else this is an update
      if (documentBeforeUpdate == null && updateOptions.isUpsert()) {
        triggerNamespace = true;
        config = syncConfig.addSynchronizedDocument(namespace, documentId);
        event = changeEventForLocalInsert(namespace, documentAfterUpdate, true);
      } else {
        triggerNamespace = false;
        config = syncConfig.getSynchronizedDocument(namespace, documentId);
        event = changeEventForLocalUpdate(
            namespace,
            BsonUtils.getDocumentId(documentAfterUpdate),
            ChangeEvent.UpdateDescription.diff(documentBeforeUpdate, documentAfterUpdate),
            documentAfterUpdate,
            true);
      }

      config.setSomePendingWrites(logicalT, event);

      if (documentBeforeUpdate != null) {
        undoCollection
            .deleteOne(getDocumentIdFilter(BsonUtils.getDocumentId(documentBeforeUpdate)));
      }
    } finally {
      lock.unlock();
    }
    if (triggerNamespace) {
      triggerListeningToNamespace(namespace);
    }
    emitEvent(documentId, event);
    return UpdateResult.acknowledged(1, 1L, updateOptions.isUpsert() ? documentId : null);
  }

  /**
   * Update all documents in the collection according to the specified arguments.
   *
   * @param filter a document describing the query filter, which may not be null.
   * @param update a document describing the update, which may not be null. The update to
   *               apply must include only update operators.
   * @return the result of the update many operation
   */
  UpdateResult updateMany(final MongoNamespace namespace,
                          final Bson filter,
                          final Bson update) {
    return updateMany(namespace, filter, update, new UpdateOptions());
  }

  /**
   * Update all documents in the collection according to the specified arguments.
   *
   * @param filter        a document describing the query filter, which may not be null.
   * @param update        a document describing the update, which may not be null. The update to
   *                      apply must include only update operators.
   * @param updateOptions the options to apply to the update operation
   * @return the result of the update many operation
   */
  UpdateResult updateMany(
      final MongoNamespace namespace,
      final Bson filter,
      final Bson update,
      final UpdateOptions updateOptions) {
    final List<ChangeEvent<BsonDocument>> eventsToEmit = new ArrayList<>();
    final UpdateResult result;
    final Lock lock =
        this.syncConfig.getNamespaceConfig(namespace).getLock().writeLock();
    lock.lock();
    try {
      // fetch all of the documents that this filter will match
      final Map<BsonValue, BsonDocument> idToBeforeDocumentMap = new HashMap<>();
      final BsonArray ids = new BsonArray();
      final MongoCollection<BsonDocument> localCollection = getLocalCollection(namespace);
      final MongoCollection<BsonDocument> undoCollection = getUndoCollection(namespace);
      localCollection
          .find(filter)
          .forEach((Block<BsonDocument>) bsonDocument -> {
            final BsonValue documentId = BsonUtils.getDocumentId(bsonDocument);
            ids.add(documentId);
            idToBeforeDocumentMap.put(documentId, bsonDocument);
            undoCollection.insertOne(bsonDocument);
          });

      // use the matched ids from prior to create a new filter.
      // this will prevent any race conditions if documents were
      // inserted between the prior find
      Bson updatedFilter = updateOptions.isUpsert()
          ? filter : new BsonDocument("_id", new BsonDocument("$in", ids));

      // do the bulk write
      result = localCollection.updateMany(updatedFilter, update, updateOptions);

      // if this was an upsert, create the post-update filter using
      // the upserted id.
      if (result.getUpsertedId() != null) {
        updatedFilter = getDocumentIdFilter(result.getUpsertedId());
      }

      // iterate over the after-update docs using the updated filter
      localCollection.find(updatedFilter).forEach(
          (Block<BsonDocument>) unsanitizedAfterDocument -> {
            // get the id of the after-update document, and fetch the before-update
            // document from the map we created from our pre-update `find`
            final BsonValue documentId = BsonUtils.getDocumentId(unsanitizedAfterDocument);
            final BsonDocument beforeDocument = idToBeforeDocumentMap.get(documentId);

            // if there was no before-update document and this was not an upsert,
            // a document that meets the filter criteria must have been
            // inserted or upserted asynchronously between this find and the update.
            if (beforeDocument == null && !updateOptions.isUpsert()) {
              return;
            }

            // Ensure that the update didn't add any forbidden fields to the document, and remove
            // them if it did.
            final BsonDocument afterDocument =
                    sanitizeCachedDocument(localCollection, unsanitizedAfterDocument, documentId);

            // because we are looking up a bulk write, we may have queried documents
            // that match the updated state, but were not actually modified.
            // if the document before the update is the same as the updated doc,
            // assume it was not modified and take no further action
            if (afterDocument.equals(beforeDocument)) {
              undoCollection.deleteOne(getDocumentIdFilter(documentId));
              return;
            }

            final CoreDocumentSynchronizationConfig config;
            final ChangeEvent<BsonDocument> event;

            // if there was no earlier document and this was an upsert,
            // treat the upsert as an insert, as far as sync is concerned
            // else treat it as a standard update
            if (beforeDocument == null && updateOptions.isUpsert()) {
              config = syncConfig.addSynchronizedDocument(namespace, documentId);
              event = changeEventForLocalInsert(namespace, afterDocument, true);
            } else {
              config = syncConfig.getSynchronizedDocument(namespace, documentId);
              event = changeEventForLocalUpdate(
                  namespace,
                  documentId,
                  ChangeEvent.UpdateDescription.diff(beforeDocument, afterDocument),
                  afterDocument,
                  true);
            }

            config.setSomePendingWrites(logicalT, event);
            undoCollection.deleteOne(getDocumentIdFilter(documentId));
            eventsToEmit.add(event);
          });
    } finally {
      lock.unlock();
    }
    if (result.getUpsertedId() != null) {
      triggerListeningToNamespace(namespace);
    }
    for (final ChangeEvent<BsonDocument> event : eventsToEmit) {
      emitEvent(BsonUtils.getDocumentId(event.getDocumentKey()), event);
    }
    return result;
  }

  /**
   * Replaces a single synchronized document by its given id with the given full document
   * replacement. No replacement will occur if the _id is not being synchronized.
   *
   * @param namespace  the namespace where the document lives.
   * @param documentId the _id of the document.
   * @param document   the replacement document.
   */
  private void updateOrUpsertOneFromResolution(
      final MongoNamespace namespace,
      final BsonValue documentId,
      final BsonDocument document,
      final BsonDocument atVersion,
      final ChangeEvent<BsonDocument> remoteEvent
  ) {
    final ChangeEvent<BsonDocument> event;
    final Lock lock =
        this.syncConfig.getNamespaceConfig(namespace).getLock().writeLock();
    lock.lock();
    try {
      final CoreDocumentSynchronizationConfig config =
          syncConfig.getSynchronizedDocument(namespace, documentId);
      if (config == null) {
        return;
      }

      final MongoCollection<BsonDocument> localCollection = getLocalCollection(namespace);
      final MongoCollection<BsonDocument> undoCollection = getUndoCollection(namespace);
      final BsonDocument documentBeforeUpdate = localCollection
          .find(getDocumentIdFilter(documentId)).first();
      if (documentBeforeUpdate != null) {
        undoCollection.insertOne(documentBeforeUpdate);
      }

      // Remove forbidden fields from the resolved document before it will updated/upserted in the
      // local collection.
      final BsonDocument docForStorage = sanitizeDocument(document);

      final BsonDocument documentAfterUpdate = localCollection
          .findOneAndReplace(
              getDocumentIdFilter(documentId),
              docForStorage,
              new FindOneAndReplaceOptions().upsert(true).returnDocument(ReturnDocument.AFTER));

      if (remoteEvent.getOperationType() == ChangeEvent.OperationType.DELETE) {
        event = changeEventForLocalInsert(namespace, documentAfterUpdate, true);
      } else {
        event = changeEventForLocalUpdate(
            namespace,
            documentId,
            ChangeEvent.UpdateDescription.diff(
                    sanitizeDocument(remoteEvent.getFullDocument()),
                    documentAfterUpdate),
            docForStorage,
            true);
      }
      config.setSomePendingWrites(
          logicalT,
          atVersion,
          event);
      if (documentBeforeUpdate != null) {
        undoCollection.deleteOne(getDocumentIdFilter(documentId));
      }
    } finally {
      lock.unlock();
    }
    emitEvent(BsonUtils.getDocumentId(event.getDocumentKey()), event);
  }

  /**
   * Replaces a single synchronized document by its given id with the given full document
   * replacement. No replacement will occur if the _id is not being synchronized.
   *
   * @param namespace  the namespace where the document lives.
   * @param documentId the _id of the document.
   * @param remoteDocument   the replacement document.
   */
  private void replaceOrUpsertOneFromRemote(
      final MongoNamespace namespace,
      final BsonValue documentId,
      final BsonDocument remoteDocument,
      final BsonDocument atVersion
  ) {
    final ChangeEvent<BsonDocument> event;
    final Lock lock =
        this.syncConfig.getNamespaceConfig(namespace).getLock().writeLock();
    lock.lock();
    try {
      final CoreDocumentSynchronizationConfig config =
          syncConfig.getSynchronizedDocument(namespace, documentId);
      if (config == null) {
        return;
      }

      final MongoCollection<BsonDocument> localCollection = getLocalCollection(namespace);
      final MongoCollection<BsonDocument> undoCollection = getUndoCollection(namespace);
      final BsonDocument documentBeforeUpdate = localCollection
          .find(getDocumentIdFilter(documentId)).first();
      if (documentBeforeUpdate != null) {
        undoCollection.insertOne(documentBeforeUpdate);
      }

      // Since we are accepting the remote document as the resolution to the conflict, it may
      // contain version information. Clone the document and remove forbidden fields from it before
      // storing it in the collection.
      final BsonDocument docForStorage = sanitizeDocument(remoteDocument);

      localCollection
          .findOneAndReplace(
              getDocumentIdFilter(documentId),
              docForStorage,
              new FindOneAndReplaceOptions().upsert(true));
      config.setPendingWritesComplete(atVersion);
      if (documentBeforeUpdate != null) {
        undoCollection.deleteOne(getDocumentIdFilter(documentId));
      }

      event = changeEventForLocalReplace(namespace, documentId, docForStorage, false);
    } finally {
      lock.unlock();
    }
    emitEvent(BsonUtils.getDocumentId(event.getDocumentKey()), event);
  }

  /**
   * Removes at most one document from the collection that matches the given filter.  If no
   * documents match, the collection is not
   * modified.
   *
   * @param filter the query filter to apply the the delete operation
   * @return the result of the remove one operation
   */
  DeleteResult deleteOne(final MongoNamespace namespace, final Bson filter) {
    final ChangeEvent<BsonDocument> event;
    final DeleteResult result;
    final Lock lock =
        this.syncConfig.getNamespaceConfig(namespace).getLock().writeLock();
    lock.lock();
    try {
      final MongoCollection<BsonDocument> localCollection = getLocalCollection(namespace);
      final BsonDocument docToDelete = localCollection
          .find(filter)
          .first();

      if (docToDelete == null) {
        return DeleteResult.acknowledged(0);
      }

      final BsonValue documentId = BsonUtils.getDocumentId(docToDelete);
      final CoreDocumentSynchronizationConfig config =
          syncConfig.getSynchronizedDocument(namespace, documentId);

      if (config == null) {
        return DeleteResult.acknowledged(0);
      }

      final MongoCollection<BsonDocument> undoCollection = getUndoCollection(namespace);
      undoCollection.insertOne(docToDelete);

      result = localCollection.deleteOne(filter);
      event = changeEventForLocalDelete(namespace, documentId, true);

      // this block is to trigger coalescence for a delete after insert
      if (config.getLastUncommittedChangeEvent() != null
          && config.getLastUncommittedChangeEvent().getOperationType()
          == ChangeEvent.OperationType.INSERT) {
        desyncDocumentFromRemote(config.getNamespace(), config.getDocumentId());
        undoCollection.deleteOne(getDocumentIdFilter(config.getDocumentId()));
        return result;
      }

      config.setSomePendingWrites(logicalT, event);
      undoCollection.deleteOne(getDocumentIdFilter(config.getDocumentId()));
    } finally {
      lock.unlock();
    }
    emitEvent(BsonUtils.getDocumentId(event.getDocumentKey()), event);
    return result;
  }

  /**
   * Removes all documents from the collection that match the given query filter.  If no documents
   * match, the collection is not modified.
   *
   * @param filter the query filter to apply the the delete operation
   * @return the result of the remove many operation
   */
  DeleteResult deleteMany(final MongoNamespace namespace,
                          final Bson filter) {
    final List<ChangeEvent<BsonDocument>> eventsToEmit = new ArrayList<>();
    final DeleteResult result;
    final Lock lock =
        this.syncConfig.getNamespaceConfig(namespace).getLock().writeLock();
    lock.lock();
    try {
      final MongoCollection<BsonDocument> localCollection = getLocalCollection(namespace);
      final MongoCollection<BsonDocument> undoCollection = getUndoCollection(namespace);
      final Set<BsonValue> idsToDelete =
          localCollection
              .find(filter)
              .map(new Function<BsonDocument, BsonValue>() {
                @Override
                @NonNull
                public BsonValue apply(@NonNull final BsonDocument bsonDocument) {
                  undoCollection.insertOne(bsonDocument);
                  return BsonUtils.getDocumentId(bsonDocument);
                }
              }).into(new HashSet<>());

      result = localCollection.deleteMany(filter);

      for (final BsonValue documentId : idsToDelete) {
        final CoreDocumentSynchronizationConfig config =
            syncConfig.getSynchronizedDocument(namespace, documentId);

        if (config == null) {
          continue;
        }

        final ChangeEvent<BsonDocument> event =
            changeEventForLocalDelete(namespace, documentId, true);

        // this block is to trigger coalescence for a delete after insert
        if (config.getLastUncommittedChangeEvent() != null
            && config.getLastUncommittedChangeEvent().getOperationType()
            == ChangeEvent.OperationType.INSERT) {
          desyncDocumentFromRemote(config.getNamespace(), config.getDocumentId());
          undoCollection.deleteOne(getDocumentIdFilter(documentId));
          continue;
        }

        config.setSomePendingWrites(
            logicalT, event);
        undoCollection.deleteOne(getDocumentIdFilter(documentId));
        eventsToEmit.add(event);
      }
    } finally {
      lock.unlock();
    }
    for (final ChangeEvent<BsonDocument> event : eventsToEmit) {
      emitEvent(BsonUtils.getDocumentId(event.getDocumentKey()), event);
    }
    return result;
  }

  /**
   * Deletes a single synchronized document by its given id. No deletion will occur if the _id is
   * not being synchronized.
   *
   * @param namespace  the namespace where the document lives.
   * @param documentId the _id of the document.
   */
  private void deleteOneFromResolution(
      final MongoNamespace namespace,
      final BsonValue documentId,
      final BsonDocument atVersion
  ) {
    final ChangeEvent<BsonDocument> event;
    final Lock lock =
        this.syncConfig.getNamespaceConfig(namespace).getLock().writeLock();
    lock.lock();
    try {
      final CoreDocumentSynchronizationConfig config =
          syncConfig.getSynchronizedDocument(namespace, documentId);
      if (config == null) {
        return;
      }

      final MongoCollection<BsonDocument> localCollection = getLocalCollection(namespace);
      final MongoCollection<BsonDocument> undoCollection = getUndoCollection(namespace);
      final BsonDocument documentToDelete = localCollection
          .find(getDocumentIdFilter(documentId)).first();
      if (documentToDelete != null) {
        undoCollection.insertOne(documentToDelete);
      }
      localCollection
          .deleteOne(getDocumentIdFilter(documentId));
      event = changeEventForLocalDelete(namespace, documentId, true);
      config.setSomePendingWrites(
          logicalT, atVersion, event);
      if (documentToDelete != null) {
        undoCollection.deleteOne(getDocumentIdFilter(documentToDelete));
      }
    } finally {
      lock.unlock();
    }
    emitEvent(documentId, event);
  }

  /**
   * Deletes a single synchronized document by its given id. No deletion will occur if the _id is
   * not being synchronized.
   *
   * @param namespace  the namespace where the document lives.
   * @param documentId the _id of the document.
   */
  private void deleteOneFromRemote(
      final MongoNamespace namespace,
      final BsonValue documentId
  ) {
    final Lock lock =
        this.syncConfig.getNamespaceConfig(namespace).getLock().writeLock();
    lock.lock();
    try {
      final CoreDocumentSynchronizationConfig config =
          syncConfig.getSynchronizedDocument(namespace, documentId);
      if (config == null) {
        return;
      }
      final MongoCollection<BsonDocument> localCollection = getLocalCollection(namespace);
      final MongoCollection<BsonDocument> undoCollection = getUndoCollection(namespace);
      final BsonDocument documentToDelete = localCollection
          .find(getDocumentIdFilter(documentId)).first();
      if (documentToDelete == null) {
        desyncDocumentFromRemote(namespace, documentId);
        return;
      }
      undoCollection.insertOne(documentToDelete);
      localCollection.deleteOne(getDocumentIdFilter(documentId));
      desyncDocumentFromRemote(namespace, documentId);
      undoCollection.deleteOne(getDocumentIdFilter(documentId));
    } finally {
      lock.unlock();
    }
    emitEvent(documentId, changeEventForLocalDelete(namespace, documentId, false));
  }

  private void triggerListeningToNamespace(final MongoNamespace namespace) {
    syncLock.lock();
    try {
      final NamespaceSynchronizationConfig nsConfig = this.syncConfig.getNamespaceConfig(namespace);
      if (nsConfig.getSynchronizedDocuments().isEmpty()) {
        instanceChangeStreamListener.removeNamespace(namespace);
        return;
      }
      if (!this.syncConfig.getNamespaceConfig(namespace).isConfigured()) {
        return;
      }
      instanceChangeStreamListener.addNamespace(namespace);
      instanceChangeStreamListener.stop(namespace);
      instanceChangeStreamListener.start(namespace);
    } catch (final Exception ex) {
      logger.error(String.format(
          Locale.US,
          "t='%d': triggerListeningToNamespace ns=%s exception: %s",
          logicalT,
          namespace,
          ex));
    } finally {
      syncLock.unlock();
    }
  }

  /**
   * Whether or not the DataSynchronizer is running in the background.
   *
   * @return true if running, false if not
   */
  public boolean isRunning() {
    return isRunning;
  }

  public boolean areAllStreamsOpen() {
    syncLock.lock();
    try {
      return instanceChangeStreamListener.areAllStreamsOpen();
    } finally {
      syncLock.unlock();
    }
  }

  /**
   * Emits a change event for the given document id.
   *
   * @param documentId the document that has a change event for it.
   * @param event      the change event.
   */
  private void emitEvent(final BsonValue documentId, final ChangeEvent<BsonDocument> event) {
    listenersLock.lock();
    try {
      final NamespaceSynchronizationConfig namespaceSynchronizationConfig =
          syncConfig.getNamespaceConfig(event.getNamespace());

      if (namespaceSynchronizationConfig.getNamespaceListenerConfig() == null) {
        return;
      }
      final NamespaceListenerConfig namespaceListener =
          namespaceSynchronizationConfig.getNamespaceListenerConfig();

      eventDispatcher.dispatch(new Callable<Object>() {
        @Override
        @SuppressWarnings("unchecked")
        public Object call() {
          try {
            if (namespaceListener.getEventListener() != null) {
              namespaceListener.getEventListener().onEvent(
                  documentId,
                  ChangeEvent.transformChangeEventForUser(
                      event, namespaceListener.getDocumentCodec()));
            }
          } catch (final Exception ex) {
            logger.error(String.format(
                Locale.US,
                "emitEvent ns=%s documentId=%s emit exception: %s",
                event.getNamespace(),
                documentId,
                ex), ex);
          }
          return null;
        }
      });
    } finally {
      listenersLock.unlock();
    }
  }
  // ----- Utilities -----

  /**
   * Returns the undo collection representing the given namespace for recording documents that
   * may need to be reverted after a system failure.
   *
   * @param namespace the namespace referring to the undo collection.
   * @return the undo collection representing the given namespace for recording documents that
   * may need to be reverted after a system failure.
   */
  private MongoCollection<BsonDocument> getUndoCollection(final MongoNamespace namespace) {
    return localClient
        .getDatabase(String.format("sync_undo_%s", namespace.getDatabaseName()))
        .getCollection(namespace.getCollectionName(), BsonDocument.class)
        .withCodecRegistry(MongoClientSettings.getDefaultCodecRegistry());
  }

  /**
   * Returns the local collection representing the given namespace.
   *
   * @param namespace   the namespace referring to the local collection.
   * @param resultClass the {@link Class} that represents documents in the collection.
   * @param <T>         the type documents in the collection.
   * @return the local collection representing the given namespace.
   */
  private <T> MongoCollection<T> getLocalCollection(
      final MongoNamespace namespace,
      final Class<T> resultClass,
      final CodecRegistry codecRegistry
  ) {
    return localClient
        .getDatabase(String.format("sync_user_%s", namespace.getDatabaseName()))
        .getCollection(namespace.getCollectionName(), resultClass)
        .withCodecRegistry(codecRegistry);
  }

  /**
   * Returns the local collection representing the given namespace for raw document operations.
   *
   * @param namespace the namespace referring to the local collection.
   * @return the local collection representing the given namespace for raw document operations.
   */
  private MongoCollection<BsonDocument> getLocalCollection(final MongoNamespace namespace) {
    return getLocalCollection(
        namespace,
        BsonDocument.class,
        MongoClientSettings.getDefaultCodecRegistry());
  }

  /**
   * Returns the remote collection representing the given namespace.
   *
   * @param namespace   the namespace referring to the remote collection.
   * @param resultClass the {@link Class} that represents documents in the collection.
   * @param <T>         the type documents in the collection.
   * @return the remote collection representing the given namespace.
   */
  private <T> CoreRemoteMongoCollection<T> getRemoteCollection(
      final MongoNamespace namespace,
      final Class<T> resultClass
  ) {
    return remoteClient
        .getDatabase(namespace.getDatabaseName())
        .getCollection(namespace.getCollectionName(), resultClass);
  }

  /**
   * Returns the remote collection representing the given namespace for raw document operations.
   *
   * @param namespace the namespace referring to the remote collection.
   * @return the remote collection representing the given namespace for raw document operations.
   */
  private CoreRemoteMongoCollection<BsonDocument> getRemoteCollection(
      final MongoNamespace namespace
  ) {
    return getRemoteCollection(namespace, BsonDocument.class);
  }

  private Set<BsonDocument> getLatestDocumentsForStaleFromRemote(
      final NamespaceSynchronizationConfig nsConfig,
      final Set<BsonValue> staleIds) {
    final BsonArray ids = new BsonArray();
    for (final BsonValue bsonValue : staleIds) {
      ids.add(new BsonDocument("_id", bsonValue));
    }

    if (ids.size() == 0) {
      return new HashSet<>();
    }

    return this.getRemoteCollection(nsConfig.getNamespace()).find(
        new Document("$or", ids)
    ).into(new HashSet<>());
  }

  /**
   * Returns a query filter searching for the given document _id.
   *
   * @param documentId the _id of the document to search for.
   * @return a query filter searching for the given document _id.
   */
  private static BsonDocument getDocumentIdFilter(final BsonValue documentId) {
    return new BsonDocument("_id", documentId);
  }

  /**
   * Given a local collection, a document fetched from that collection, and its _id, ensure that
   * the document does not contain forbidden fields (currently just the document version field),
   * and remove them from the document and the local collection. If no changes are made, the
   * original document reference is returned. If changes are made, a cloned copy of the document
   * with the changes will be returned.
   *
   * @param localCollection the local MongoCollection from which the document was fetched
   * @param document the document fetched from the local collection. this argument may be mutated
   * @param documentId the _id of the fetched document (taken as an arg so that if the caller
   *                   already knows the _id, the document need not be traversed to find it)
   * @return a BsonDocument without any forbidden fields.
   */
  private static BsonDocument sanitizeCachedDocument(
          final MongoCollection<BsonDocument> localCollection,
          final BsonDocument document,
          final BsonValue documentId
  ) {
    if (document == null) {
      return null;
    }
    if (document.containsKey(DOCUMENT_VERSION_FIELD)) {
      final BsonDocument clonedDoc = sanitizeDocument(document);

      final BsonDocument removeVersionUpdate =
              new BsonDocument("$unset",
                      new BsonDocument(DOCUMENT_VERSION_FIELD, new BsonInt32(1))
              );

      localCollection.findOneAndUpdate(getDocumentIdFilter(documentId), removeVersionUpdate);
      return clonedDoc;
    }

    return document;
  }

  /**
   * Given a BSON document, remove any forbidden fields and return the document. If no changes are
   * made, the original document reference is returned. If changes are made, a cloned copy of the
   * document with the changes will be returned.
   *
   * @param document the document from which to remove forbidden fields
   *
   * @return a BsonDocument without any forbidden fields.
   */
  static BsonDocument sanitizeDocument(final BsonDocument document) {
    if (document == null) {
      return null;
    }
    if (document.containsKey(DOCUMENT_VERSION_FIELD)) {
      final BsonDocument clonedDoc = document.clone();
      clonedDoc.remove(DOCUMENT_VERSION_FIELD);

      return clonedDoc;
    }
    return document;
  }

  /**
   * Adds and returns a document with a new version to the given document.
   *
   * @param document   the document to attach a new version to.
   * @param newVersion the version to attach to the document
   * @return a document with a new version to the given document.
   */
  private static BsonDocument withNewVersion(
      final BsonDocument document,
      final BsonDocument newVersion
  ) {
    final BsonDocument newDocument = BsonUtils.copyOfDocument(document);
    newDocument.put(DOCUMENT_VERSION_FIELD, newVersion);
    return newDocument;
  }
}
