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

import com.mongodb.Block;
import com.mongodb.Function;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoNamespace;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.DeleteManyModel;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.FindOneAndReplaceOptions;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.lang.NonNull;
import com.mongodb.stitch.core.StitchClientErrorCode;
import com.mongodb.stitch.core.StitchClientException;
import com.mongodb.stitch.core.StitchServiceErrorCode;
import com.mongodb.stitch.core.StitchServiceException;
import com.mongodb.stitch.core.internal.common.AuthMonitor;
import com.mongodb.stitch.core.internal.common.BsonUtils;
import com.mongodb.stitch.core.internal.common.Callback;
import com.mongodb.stitch.core.internal.common.Dispatcher;
import com.mongodb.stitch.core.internal.net.NetworkMonitor;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.ChangeEvent;
import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener;
import com.mongodb.stitch.core.services.mongodb.remote.OperationType;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult;
import com.mongodb.stitch.core.services.mongodb.remote.UpdateDescription;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoClient;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoCollection;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler;
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.SyncFrequency.Scheduled;
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.SyncFrequency.SyncFrequencyType;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonValue;
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
  public static final String DOCUMENT_VERSION_FIELD = "__stitch_sync_version";

  private static final int SYNC_PROTOCOL_VERSION = 1;

  private final CoreStitchServiceClient service;
  private final CoreRemoteMongoClient remoteClient;
  private final NetworkMonitor networkMonitor;
  private final AuthMonitor authMonitor;
  private final Logger logger;
  private final Lock syncLock;
  private final String instanceKey;
  private final Dispatcher dispatcher;
  private final EventDispatcher eventDispatcher;

  private MongoClient localClient;
  private MongoDatabase configDb;
  private MongoCollection<InstanceSynchronizationConfig> instancesColl;
  private InstanceChangeStreamListener instanceChangeStreamListener;
  private InstanceSynchronizationConfig syncConfig;

  private boolean syncThreadEnabled = true;
  private boolean listenersEnabled = true;
  private boolean isConfigured = false;
  private boolean isRunning = false;
  private Thread syncThread;
  private long logicalT = 0; // The current logical time or sync iteration.

  private ExceptionListener exceptionListener;
  private Thread initThread;
  private DispatchGroup ongoingOperationsGroup;

  public DataSynchronizer(
      final String instanceKey,
      final CoreStitchServiceClient service,
      final MongoClient localClient,
      final CoreRemoteMongoClient remoteClient,
      final NetworkMonitor networkMonitor,
      final AuthMonitor authMonitor,
      final Dispatcher dispatcher
  ) {
    this.service = service;
    this.localClient = localClient;
    this.remoteClient = remoteClient;
    this.networkMonitor = networkMonitor;
    this.authMonitor = authMonitor;
    this.syncLock = new ReentrantLock();
    this.dispatcher = dispatcher;
    this.eventDispatcher = new EventDispatcher(instanceKey, dispatcher);
    this.instanceKey = instanceKey;
    this.ongoingOperationsGroup = new DispatchGroup();
    this.logger =
        Loggers.getLogger(String.format("DataSynchronizer-%s", instanceKey));
    if (this.networkMonitor != null) {
      this.networkMonitor.addNetworkStateListener(this);
    }

    this.initThread = new Thread(() -> {
      initialize();
      recover();
    }, "dataSynchronizerInitializationThread");

    this.initThread.start();
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
  void recover() {
    final List<NamespaceSynchronizationConfig> nsConfigs = new ArrayList<>();
    for (final MongoNamespace ns : this.syncConfig.getSynchronizedNamespaces()) {
      nsConfigs.add(this.syncConfig.getNamespaceConfig(ns));
    }

    for (final NamespaceSynchronizationConfig nsConfig : nsConfigs) {
      nsConfig.getLock().writeLock().lock();
    }
    try {
      for (final NamespaceSynchronizationConfig nsConfig : nsConfigs) {
        nsConfig.getLock().writeLock().lock();
        try {
          recoverNamespace(nsConfig);
        } finally {
          nsConfig.getLock().writeLock().unlock();
        }
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
    final List<BsonDocument> undoDocs = undoCollection.find().into(new ArrayList<>());
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
            new BsonArray(new ArrayList<>(
                this.syncConfig.getSynchronizedDocumentIds(nsConfig.getNamespace()))))));
  }

  public InstanceSynchronizationConfig getSyncConfig() {
    return syncConfig;
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
    ongoingOperationsGroup.blockAndWait();
    this.localClient = localClient;

    initThread = new Thread(() -> {
      this.stop();
      initialize();
      this.start();
      ongoingOperationsGroup.unblock();
    }, "dataSynchronizerReinitializationThread");

    this.initThread.start();
  }

  /**
   * Reloads the synchronization config. This wipes all in-memory synchronization settings.
   */
  public void wipeInMemorySettings() {
    this.waitUntilInitialized();

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

  // Method used for testing: returns if the DataSynchronizer has an nsConfig for namespace
  public boolean hasNamespaceListener(@Nonnull final MongoNamespace namespace) {
    this.waitUntilInitialized();
    return instanceChangeStreamListener.hasNamespace(namespace);
  }

  // Method used for testing: returns if the nsConfig's stream is open for namespace
  public boolean isNamespaceListenerOpen(@Nonnull final MongoNamespace namespace) {
    this.waitUntilInitialized();
    return instanceChangeStreamListener.isOpen(namespace);
  }

  public void configureSyncFrequency(@Nonnull final MongoNamespace namespace,
                                     @Nullable final SyncFrequency syncFrequency) {
    final SyncFrequency newFrequency = syncFrequency != null
        ? syncFrequency : SyncFrequency.reactive();

    // Set old frequency to onDemand preliminarily because that had no streams or timers
    SyncFrequency oldFrequency = SyncFrequency.onDemand();

    // Get the nsConfig and get its syncFrequency
    final NamespaceSynchronizationConfig nsConfig = syncConfig.getNamespaceConfig(namespace);
    if (nsConfig != null && nsConfig.getSyncFrequency() != null) {
      oldFrequency = nsConfig.getSyncFrequency();
    }

    // Set the nsConfig to have a new syncFrequency
    nsConfig.setSyncFrequency(newFrequency);

    // Trigger listening on the namespace
    triggerListeningToNamespace(namespace);

    // Iterate over all possible values for oldFrequency
    switch (oldFrequency.getType()) {
      case REACTIVE:
        switch (newFrequency.getType()) {
          case REACTIVE:
            // REACTIVE -> REACTIVE: Nothing should change, just return
            return;
          case SCHEDULED:
            if (((Scheduled) newFrequency).isConnected()) {
              // REACTIVE --> SCHEDULED AND isConnected is TRUE:
              // Schedule a new timer AND maintain the existing stream
              // TODO schedule a new timer
              return;
            } else {
              // REACTIVE --> SCHEDULED AND isConnected is FALSE:
              // Schedule a new timer AND close the existing stream
              instanceChangeStreamListener.removeNamespace(namespace);
              // TODO schedule a new timer
              return;
            }
          case ON_DEMAND:
            // REACTIVE --> ON_DEMAND:
            // Close the existing change stream
            instanceChangeStreamListener.removeNamespace(namespace);
            return;
          default:
            return;
        }
      case SCHEDULED:
        if (((Scheduled) oldFrequency).isConnected()) {
          switch (newFrequency.getType()) {
            case REACTIVE:
              // SCHEDULED AND isConnected is TRUE --> REACTIVE:
              // Cancel the scheduled time AND do a sync pass on the collection
              // TODO cancel the timer and do sync pass on the namespace
              return;
            case SCHEDULED:
              if (((Scheduled) newFrequency).isConnected()) {
                // SCHEDULED AND isConnected is TRUE --> SCHEDULED and isConnected is TRUE:
                // Cancel the scheduled time AND schedule a new timer
                // TODO schedule a new timer and delete the old one
                return;
              } else {
                // SCHEDULED AND isConnected is TRUE --> SCHEDULED and isConnected is FALSE:
                // Cancel the scheduled time AND schedule a new timer AND close the existing stream
                instanceChangeStreamListener.removeNamespace(namespace);
                // TODO schedule a new timer and delete the old one
                return;
              }
            case ON_DEMAND:
              // If a user changes from SCHEDULED AND isConnected is TRUE to ON_DEMAND:
              // Cancel the scheduled timer and close the existing stream
              instanceChangeStreamListener.removeNamespace(namespace);
              // TODO cancel the scheduled timer
              return;
            default:
              return;
          }
        } else {
          switch (newFrequency.getType()) {
            case REACTIVE:
              // SCHEDULED AND isConnected is FALSE --> REACTIVE:
              // Cancel the scheduled time AND do a sync pass on the collection
              // AND open a new change stream
              // TODO cancel the timer and do sync pass on the namespace
              return;
            case SCHEDULED:
              if (((Scheduled) newFrequency).isConnected()) {
                // SCHEDULED AND isConnected is FALSE --> SCHEDULED and isConnected is TRUE:
                // Cancel the scheduled time AND schedule a new timer AND open a new stream
                // TODO schedule a new timer and delete the old one
                return;
              } else {
                // SCHEDULED AND isConnected is FALSE --> SCHEDULED and isConnected is FALSE:
                // Cancel the scheduled time AND schedule a new timer
                // TODO schedule a new timer and delete the old one
                return;
              }
            case ON_DEMAND:
              // SCHEDULED AND isConnected is FALSE --> ON_DEMAND:
              // Cancel the scheduled timer
              // TODO cancel the scheduled timer
              return;
            default:
              return;
          }
        }
      case ON_DEMAND:
        switch (newFrequency.getType()) {
          case REACTIVE:
            // ON_DEMAND --> REACTIVE:
            // Open a new change stream listener
            return;
          case SCHEDULED:
            if (((Scheduled) newFrequency).isConnected()) {
              // ON_DEMAND --> SCHEDULED AND isConnected is TRUE:
              // Schedule a new timer AND open a new stream
              // TODO schedule a new timer
              return;
            } else {
              // ON_DEMAND --> SCHEDULED AND isConnected is FALSE:
              // Schedule a new sync pass timer
              // TODO schedule a new timer
              return;
            }
          case ON_DEMAND:
            // ON_DEMAND --> ON_DEMAND: nothing to change, just return
            return;
          default:
            return;
        }
      default:
        return;
    }
  }

  public void configure(@Nonnull final MongoNamespace namespace,
                        @Nonnull final SyncConfiguration syncConfiguration) {
    this.waitUntilInitialized();

    this.exceptionListener = syncConfiguration.getExceptionListener();

    this.syncConfig.getNamespaceConfig(namespace).configure(syncConfiguration);

    syncLock.lock();
    if (!this.isConfigured) {
      this.isConfigured = true;
      syncLock.unlock();
    } else {
      syncLock.unlock();
    }

    this.configureSyncFrequency(namespace, syncConfiguration.getSyncFrequency());

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
      if (listenersEnabled) {
        instanceChangeStreamListener.start();
      }

      if (syncThread == null) {
        syncThread = new Thread(
            new DataSynchronizerRunner(
              new WeakReference<>(this),
              networkMonitor,
              logger
            ),
            "dataSynchronizerRunnerThread"
        );
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

  public void disableListeners() {
    syncLock.lock();
    try {
      listenersEnabled = false;
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
    this.waitUntilInitialized();

    this.ongoingOperationsGroup.blockAndWait();

    syncLock.lock();
    try {
      if (this.networkMonitor != null) {
        this.networkMonitor.removeNetworkStateListener(this);
      }
      this.dispatcher.close();
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
        if (logger.isInfoEnabled()) {
          logger.info("reached max logical time; resetting back to 0");
        }
        logicalT = 0;
      }
      logicalT++;

      if (logger.isInfoEnabled()) {
        logger.info(String.format(
            Locale.US,
            "t='%d': doSyncPass START",
            logicalT));
      }
      if (networkMonitor == null || !networkMonitor.isConnected()) {
        if (logger.isInfoEnabled()) {
          logger.info(String.format(
              Locale.US,
              "t='%d': doSyncPass END - Network disconnected",
              logicalT));
        }
        return false;
      }
      if (authMonitor == null || !authMonitor.tryIsLoggedIn()) {
        if (logger.isInfoEnabled()) {
          logger.info(String.format(
              Locale.US,
              "t='%d': doSyncPass END - Logged out",
              logicalT));
        }
        return false;
      }

      syncRemoteToLocal();
      syncLocalToRemote();

      if (logger.isInfoEnabled()) {
        logger.info(String.format(
            Locale.US,
            "t='%d': doSyncPass END",
            logicalT));
      }
    } catch (InterruptedException e) {
      if (logger.isInfoEnabled()) {
        logger.info(String.format(
            Locale.US,
            "t='%d': doSyncPass INTERRUPTED",
            logicalT));
      }
      return false;
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
  private void syncRemoteToLocal() throws InterruptedException {
    if (logger.isInfoEnabled()) {
      logger.info(String.format(
          Locale.US,
          "t='%d': syncRemoteToLocal START",
          logicalT));
    }

    // 2. Run remote to local (R2L) sync routine
    for (final NamespaceSynchronizationConfig nsConfig : syncConfig) {
      // lock the NamespaceChangeStreamListener for this namespace to prevent a new stream from
      // opening for this namespace during this sync pass.
      final ReadWriteLock streamerLock = instanceChangeStreamListener
          .getLockForNamespace(nsConfig.getNamespace());

      streamerLock.writeLock().lock();
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

        final MongoNamespace namespace = nsConfig.getNamespace();
        final LocalSyncWriteModelContainer localSyncWriteModelContainer =
            new LocalSyncWriteModelContainer(nsConfig,
                                             getLocalCollection(namespace),
                                             getRemoteCollection(namespace),
                                             getUndoCollection(namespace),
                                             eventDispatcher);

        // a. For each unprocessed change event
        for (final Map.Entry<BsonValue, ChangeEvent<BsonDocument>> eventEntry :
            remoteChangeEvents.entrySet()) {
          if (logger.isDebugEnabled()) {
            logger.debug(String.format(
                Locale.US,
                "t='%d': syncRemoteToLocal consuming event of type: %s",
                logicalT,
                eventEntry.getValue().getOperationType()));
          }

          final CoreDocumentSynchronizationConfig docConfig =
              nsConfig.getSynchronizedDocument(
                  BsonUtils.getDocumentId(eventEntry.getValue().getDocumentKey()));
          if (docConfig == null || docConfig.isPaused()) {
            continue;
          }

          docConfig.getLock().readLock().lock();
          try {
            unseenIds.remove(docConfig.getDocumentId());
            latestDocumentMap.remove(docConfig.getDocumentId());
          } finally {
            docConfig.getLock().readLock().unlock();
          }

          localSyncWriteModelContainer.merge(syncRemoteChangeEventToLocal(nsConfig, docConfig,
              eventEntry.getValue()));
        }

        // For synchronized documents that had no unprocessed change event, but were marked as
        // stale, synthesize a remote replace event to replace the local stale document with the
        // latest remote copy.
        for (final BsonValue docId : unseenIds) {
          final CoreDocumentSynchronizationConfig docConfig =
              nsConfig.getSynchronizedDocument(docId);
          if (docConfig == null) {
            continue;
          }
          docConfig.getLock().readLock().lock();

          final BsonDocument version;
          final boolean isPaused;
          final boolean hasUncommittedWrites;
          try {
            isPaused = docConfig.isPaused();
            version = docConfig.getLastKnownRemoteVersion();
            hasUncommittedWrites = docConfig.hasUncommittedWrites();
          } finally {
            docConfig.getLock().readLock().unlock();
          }

          if (latestDocumentMap.containsKey(docId) && !isPaused) {
            localSyncWriteModelContainer.merge(syncRemoteChangeEventToLocal(
                nsConfig,
                docConfig,
                ChangeEvents.changeEventForLocalReplace(
                    nsConfig.getNamespace(),
                    docId,
                    latestDocumentMap.get(docId),
                    false
                )));
            continue;
          }

          // For synchronized documents that had no unprocessed change event, and did not have a
          // latest version when stale documents were queried, synthesize a remote delete event to
          // delete the local document.
          if (version != null && !isPaused) {
            localSyncWriteModelContainer.merge(syncRemoteChangeEventToLocal(
                nsConfig,
                docConfig,
                ChangeEvents.changeEventForLocalDelete(
                    nsConfig.getNamespace(),
                    docId,
                    hasUncommittedWrites
                )));
          }

          docConfig.setStale(false);
        }

        localSyncWriteModelContainer.commitAndClear();
        nsConfig.setStale(false);
      } finally {
        nsConfig.getLock().writeLock().unlock();
        streamerLock.writeLock().unlock();
      }
    }

    if (logger.isInfoEnabled()) {
      logger.info(String.format(
          Locale.US,
          "t='%d': syncRemoteToLocal END",
          logicalT));
    }
  }

  /**
   * Attempts to synchronize the given remote change event into the local database.
   *
   * @param nsConfig          the namespace configuration.
   * @param docConfig         the document configuration related to the event.
   * @param remoteChangeEvent the remote change event to synchronize into the local database.
   */
  @CheckReturnValue
  private @Nullable
  LocalSyncWriteModelContainer syncRemoteChangeEventToLocal(
      final NamespaceSynchronizationConfig nsConfig,
      final CoreDocumentSynchronizationConfig docConfig,
      final ChangeEvent<BsonDocument> remoteChangeEvent
  ) {
    SyncAction action = null;
    SyncMessage message = null;
    Exception syncException = null;

    docConfig.getLock().readLock().lock();
    try {
      // if logical time hasn't advanced...
      if (docConfig.hasUncommittedWrites() && docConfig.getLastResolution() == logicalT) {
        action = SyncAction.WAIT;
        message = SyncMessage.SIMULTANEOUS_WRITES_MESSAGE;
      }

      if (logger.isDebugEnabled()) {
        logger.debug(String.format(
            Locale.US,
            SyncMessage.BASE_MESSAGE + " processing remote operation='%s'",
            logicalT,
            nsConfig.getNamespace(),
            docConfig.getDocumentId(),
            SyncMessage.R2L_METHOD,
            remoteChangeEvent.getOperationType().toString()));
      }

      // record whether this is an insert or delete operation
      final boolean isInsert;
      final boolean isDelete;
      switch (remoteChangeEvent.getOperationType()) {
        case INSERT:
          isDelete = false;
          isInsert = true;
          break;
        case REPLACE:
        case UPDATE:
          isDelete = false;
          isInsert = false;
          break;
        case DELETE:
          isDelete = true;
          isInsert = false;
          break;
        default:
          // unknown operation
          isDelete = false;
          isInsert = false;
          action = SyncAction.DROP_EVENT_AND_PAUSE;
          message = SyncMessage.UNKNOWN_OPTYPE_MESSAGE;
          syncException = new Exception() {
            public String toString() {
              return remoteChangeEvent.getOperationType().toString();
            }
          };
      }

      DocumentVersionInfo remoteVersionInfo = null;
      if (action == null) { // if we haven't encountered an error
        try {
          remoteVersionInfo = DocumentVersionInfo
              .getRemoteVersionInfo(remoteChangeEvent.getFullDocument());
        } catch (final Exception e) {
          action = SyncAction.DROP_EVENT_AND_DESYNC;
          message = SyncMessage.CANNOT_PARSE_REMOTE_VERSION_MESSAGE;
        }
      }

      if (action == null) { // if we haven't encountered an error
        if (remoteVersionInfo.hasVersion()
            && remoteVersionInfo.getVersion().getSyncProtocolVersion() != SYNC_PROTOCOL_VERSION) {
          action = SyncAction.DROP_EVENT_AND_DESYNC;
          message = SyncMessage.UNKNOWN_REMOTE_PROTOCOL_VERSION_MESSAGE;
          final DocumentVersionInfo versionInfoForSyncException = remoteVersionInfo;
          syncException = new Exception() {
            @Override
            public String toString() {
              return Integer.toString(
                  versionInfoForSyncException.getVersion().getSyncProtocolVersion());
            }
          };
        } else {
          // sync protocol versions match
          final DocumentVersionInfo lastSeenVersionInfo =
              DocumentVersionInfo.fromVersionDoc(docConfig.getLastKnownRemoteVersion());

          final boolean remoteHasNoVersion = !remoteVersionInfo.hasVersion();
          final boolean lastSeenHasNoVersion = !lastSeenVersionInfo.hasVersion();

          @Nullable final DocumentVersionInfo.Version remoteVersion =
              remoteHasNoVersion ? null : remoteVersionInfo.getVersion();
          @Nullable final DocumentVersionInfo.Version lastSeenVersion =
              lastSeenHasNoVersion ? null : lastSeenVersionInfo.getVersion();

          final BsonDocument remoteFullDocument = remoteChangeEvent.getFullDocument();

          final long lastSeenHash;
          if (lastSeenHasNoVersion && docConfig.getLastUncommittedChangeEvent() != null) {
            // do a hash calculation if local is unversioned and we have a pending write
            lastSeenHash = HashUtils.hash(sanitizeDocument(
                docConfig.getLastUncommittedChangeEvent().getFullDocument()));
          } else {
            // use the last seen hash version
            lastSeenHash = docConfig.getLastKnownHash();
          }
          final long remoteHash = HashUtils.hash(sanitizeDocument(remoteFullDocument));

          if (docConfig.getLastUncommittedChangeEvent() == null) {
            /* No Pending Write */
            if (isDelete) {
              message = SyncMessage.DELETE_FROM_REMOTE_MESSAGE;
              action = SyncAction.DELETE_LOCAL_DOC;
            } else if (remoteHasNoVersion) {
              // apply appropriate unversioned event logic
              message = SyncMessage.EMPTY_VERSION_MESSAGE;
              if (lastSeenHasNoVersion) {
                action = SyncAction.APPLY_AND_VERSION_FROM_REMOTE;
              } else {
                action = lastSeenHash != remoteHash
                    ? SyncAction.APPLY_FROM_REMOTE : SyncAction.DROP_EVENT;
              }
            } else if (lastSeenHasNoVersion) {
              action = lastSeenHash != remoteHash
                  ? SyncAction.APPLY_FROM_REMOTE : SyncAction.DROP_EVENT;
              message = SyncMessage.EMPTY_VERSION_MESSAGE;
            } else {
              if (lastSeenVersion.getSyncProtocolVersion() != SYNC_PROTOCOL_VERSION) {
                action = SyncAction.DELETE_LOCAL_DOC_AND_DESYNC; // until we implement migrations
                message = SyncMessage.STALE_PROTOCOL_VERSION_MESSAGE;
                syncException = new Exception() {
                  @Override
                  public String toString() {
                    return Integer.toString(lastSeenVersion.getSyncProtocolVersion());
                  }
                };
              } else {
                if (!lastSeenVersion.getInstanceId().equals(remoteVersion.getInstanceId())) {
                  // different client generated the remote event
                  action = SyncAction.REMOTE_FIND;
                  message = SyncMessage.INSTANCE_ID_MISMATCH_MESSAGE;
                } else {
                  // check versions and apply corresponding logic
                  final long remoteVersionCounter = remoteVersion.getVersionCounter();
                  final long lastSeenVersionCounter = lastSeenVersion.getVersionCounter();

                  if (remoteVersionCounter > lastSeenVersionCounter) {
                    action = SyncAction.APPLY_FROM_REMOTE;
                    message = SyncMessage.APPLY_FROM_REMOTE_MESSAGE;
                  } else if (remoteVersionCounter == lastSeenVersionCounter
                      && lastSeenHash != remoteHash && !isInsert) {
                    action = SyncAction.APPLY_FROM_REMOTE;
                    message = SyncMessage.REMOTE_UPDATE_WITHOUT_VERSION_MESSAGE;
                  } else {
                    action = SyncAction.DROP_EVENT;
                    message = SyncMessage.PROBABLY_GENERATED_BY_US_MESSAGE;
                  }
                }
              }
            }
          } else {
            /* Pending Write Exists */
            if (isDelete) {
              final OperationType uncommittedOpType =
                  docConfig.getLastUncommittedChangeEvent().getOperationType();

              message = SyncMessage.PENDING_WRITE_DELETE_MESSAGE;
              action = uncommittedOpType == OperationType.REPLACE
                  || uncommittedOpType == OperationType.UPDATE
                  ? SyncAction.CONFLICT : SyncAction.DROP_EVENT;
            } else if (remoteHasNoVersion || lastSeenHasNoVersion) {
              // apply appropriate unversioned event logic
              message = SyncMessage.PENDING_WRITE_EMPTY_VERSION_MESSAGE;
              if (remoteHasNoVersion == lastSeenHasNoVersion) {
                action = lastSeenHash != remoteHash ? SyncAction.CONFLICT : SyncAction.DROP_EVENT;
              } else {
                action = SyncAction.CONFLICT;
              }
            } else {
              if (!lastSeenVersion.getInstanceId().equals(remoteVersion.getInstanceId())) {
                // different client generated the remote event
                action = SyncAction.REMOTE_FIND;
                message = SyncMessage.INSTANCE_ID_MISMATCH_MESSAGE;
              } else {
                // check versions and apply corresponding logic
                final long remoteVersionCounter = remoteVersion.getVersionCounter();
                final long lastSeenVersionCounter = lastSeenVersion.getVersionCounter();

                if (remoteVersionCounter > lastSeenVersionCounter) {
                  message = SyncMessage.STALE_LOCAL_WRITE_MESSAGE;
                  action = SyncAction.CONFLICT;
                } else {
                  message = SyncMessage.STALE_EVENT_MESSAGE;
                  action = lastSeenHash != remoteHash && !isInsert
                      ? SyncAction.CONFLICT : SyncAction.DROP_EVENT;
                }
              }
            }
          }
        }
      }
    } finally {
      docConfig.getLock().readLock().unlock();
    }
    return enqueueAction(nsConfig, docConfig, remoteChangeEvent, action, message,
        SyncMessage.R2L_METHOD, syncException);
  }

  /**
   * Synchronizes the local state of every requested document to be synchronized with the remote
   * state of said documents. Any conflicts that occur will be resolved locally and later relayed
   * remotely on a subsequent iteration of {@link DataSynchronizer#doSyncPass()}.
   */
  private void syncLocalToRemote() {
    if (logger.isInfoEnabled()) {
      logger.info(String.format(
          Locale.US,
          "t='%d': syncLocalToRemote START",
          logicalT));
    }

    // 1. Run local to remote (L2R) sync routine
    // Search for modifications in each namespace.
    for (final NamespaceSynchronizationConfig nsConfig : syncConfig) {
      // lock the NamespaceChangeStreamListener for this namespace to prevent a new stream from
      // opening for this namespace during this sync pass.
      final ReadWriteLock streamerLock = instanceChangeStreamListener
          .getLockForNamespace(nsConfig.getNamespace());

      streamerLock.writeLock().lock();
      nsConfig.getLock().writeLock().lock();
      try {
        final CoreRemoteMongoCollection<BsonDocument> remoteColl =
            getRemoteCollection(nsConfig.getNamespace());

        final LocalSyncWriteModelContainer localSyncWriteModelContainer =
            newWriteModelContainer(nsConfig);

        // a. For each document that has local writes pending
        for (final CoreDocumentSynchronizationConfig docConfig : nsConfig) {
          if (!docConfig.hasUncommittedWrites() || docConfig.isPaused()) {
            continue;
          }
          docConfig.getLock().readLock().lock();

          BsonDocument nextVersion = null;
          boolean setPendingWritesComplete = false;
          SyncAction action = null;
          SyncMessage message = null;
          ChangeEvent<BsonDocument> remoteChangeEvent = null;
          Exception syncException = null;

          boolean suppressLocalEvent = false;

          try {
            if (docConfig.getLastResolution() == logicalT) {
              action = SyncAction.WAIT;
              message = SyncMessage.SIMULTANEOUS_WRITES_MESSAGE;
            } else {
              // i. Retrieve the change event for this local document in the local config metadata
              final ChangeEvent<BsonDocument> localChangeEvent =
                  docConfig.getLastUncommittedChangeEvent();
              if (logger.isDebugEnabled()) {
                logger.debug(String.format(
                    Locale.US,
                    "t='%d': syncLocalToRemote ns=%s documentId=%s processing local operation='%s'",
                    logicalT,
                    nsConfig.getNamespace(),
                    docConfig.getDocumentId(),
                    localChangeEvent.getOperationType().toString()));
              }

              final BsonDocument localDoc = localChangeEvent.getFullDocument();
              final BsonDocument docFilter = getDocumentIdFilter(docConfig.getDocumentId());

              // This is here as an optimization in case an op requires we look up the remote
              // document in advance and we only want to do this once.
              BsonDocument remoteDocument = null;
              boolean remoteDocumentFetched = false;

              final DocumentVersionInfo localVersionInfo =
                  DocumentVersionInfo.getLocalVersionInfo(docConfig);

              // ii. Check if the internal remote change stream listener has an unprocessed event
              // for this document.
              final ChangeEvent<BsonDocument> unprocessedRemoteEvent =
                  instanceChangeStreamListener.getUnprocessedEventForDocumentId(
                      nsConfig.getNamespace(),
                      docConfig.getDocumentId());

              if (unprocessedRemoteEvent != null) {
                DocumentVersionInfo unprocessedEventVersionInfo;
                try {
                  unprocessedEventVersionInfo = DocumentVersionInfo
                      .getRemoteVersionInfo(unprocessedRemoteEvent.getFullDocument());
                } catch (final Exception e) {
                  action = SyncAction.DROP_EVENT_AND_DESYNC;
                  message = SyncMessage.CANNOT_PARSE_REMOTE_VERSION_MESSAGE;
                  unprocessedEventVersionInfo = null;
                  suppressLocalEvent = true;
                }

                if (unprocessedEventVersionInfo != null) {
                  final boolean unprocessedEventHasNoVersion =
                      !unprocessedEventVersionInfo.hasVersion();
                  final boolean localHasNoVersion = !localVersionInfo.hasVersion();

                  @Nullable final DocumentVersionInfo.Version unprocessedEventVersion =
                      unprocessedEventHasNoVersion ? null :
                          unprocessedEventVersionInfo.getVersion();
                  @Nullable final DocumentVersionInfo.Version localVersion =
                      localHasNoVersion ? null : localVersionInfo.getVersion();

                  if (!unprocessedEventHasNoVersion && !localHasNoVersion) {
                    final boolean instanceIdMatch = localVersion.getInstanceId()
                        .equals(unprocessedEventVersion.getInstanceId());
                    final boolean lastSeenOlderThanRemote =
                        unprocessedEventVersion.getVersionCounter()
                            >= localVersion.getVersionCounter();

                    final boolean hasCommittedVersion = instanceIdMatch
                        && localVersion.getSyncProtocolVersion() == SYNC_PROTOCOL_VERSION
                        && !lastSeenOlderThanRemote;

                    if (!hasCommittedVersion) {
                      action = SyncAction.CONFLICT;
                      message = SyncMessage.VERSION_DIFFERENT_UNPROCESSED_EVENT_MESSAGE;
                    }
                  }
                }
                // 2. Otherwise, the unprocessed event can be safely dropped and ignored in future
                // R2L passes. Continue on to checking the operation type.
              }

              if (action == null) { // if we haven't encountered an error/conflict already
                // iii. Check the operation type
                switch (localChangeEvent.getOperationType()) {
                  // 1. INSERT
                  case INSERT: {
                    nextVersion =
                        DocumentVersionInfo.getFreshVersionDocument();

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
                        action = SyncAction.DROP_EVENT_AND_PAUSE;
                        message = SyncMessage.EXCEPTION_INSERT;
                        syncException = ex;
                        suppressLocalEvent = true;
                      } else {
                        // ii. Otherwise record that a conflict has occurred.
                        action = SyncAction.CONFLICT;
                        message = SyncMessage.DUPLICATE_KEY_EXCEPTION_MESSAGE;
                      }
                    }
                    break;
                  }


                  // 2. REPLACE
                  case REPLACE: {
                    if (localDoc == null) {
                      final IllegalStateException illegalStateException = new IllegalStateException(
                          "expected document to exist for local replace change event: %s");

                      pauseDocument(docConfig);
                      emitError(
                          docConfig,
                          illegalStateException.getMessage(),
                          illegalStateException
                      );
                      continue;
                    }
                    nextVersion = localVersionInfo.getNextVersion();
                    final BsonDocument nextDoc = withNewVersion(localDoc, nextVersion);

                    // a. Update the document in the remote database using a query for the _id and
                    // the version with an update containing the replacement document with the
                    // version counter incremented by 1.
                    RemoteUpdateResult result = null;
                    try {
                      result = remoteColl.updateOne(
                          localVersionInfo.getFilter(),
                          nextDoc);
                    } catch (final StitchServiceException ex) {
                      action = SyncAction.DROP_EVENT_AND_PAUSE;
                      message = SyncMessage.EXCEPTION_REPLACE;
                      syncException = ex;
                      suppressLocalEvent = true;
                    }
                    // c. If no documents are matched, record that a conflict has occurred.
                    if (result != null && result.getMatchedCount() == 0) {
                      action = SyncAction.CONFLICT;
                      message = SyncMessage.VERSION_DIFFERENT_REPLACED_DOC_MESSAGE;
                    }
                    break;
                  }

                  // 3. UPDATE
                  case UPDATE: {
                    if (localDoc == null) {
                      final IllegalStateException illegalStateException = new IllegalStateException(
                          SyncMessage.EXPECTED_LOCAL_DOCUMENT_TO_EXIST_MESSAGE.toString());

                      action = SyncAction.DROP_EVENT_AND_PAUSE;
                      message = SyncMessage.EXPECTED_LOCAL_DOCUMENT_TO_EXIST_MESSAGE;
                      syncException = illegalStateException;
                      suppressLocalEvent = true;
                    } else {
                      final UpdateDescription localUpdateDescription =
                          localChangeEvent.getUpdateDescription();
                      if (localUpdateDescription.getRemovedFields().isEmpty()
                          && localUpdateDescription.getUpdatedFields().isEmpty()) {
                        // if the translated update is empty, then this update is a noop, and we
                        // shouldn't update because it would improperly update the version
                        // information.
                        action = SyncAction.DROP_EVENT;
                        message = SyncMessage.EMPTY_UPDATE_DESCRIPTION;
                        suppressLocalEvent = true;
                      } else {
                        // a. Update the document in the remote database using a query for the _id
                        // and the version with an update containing the replacement document with
                        // the version counter incremented by 1.

                        // create an update document from the local change event's update
                        // description, and set the version of the new document to the next logical
                        // version
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

                        RemoteUpdateResult result = null;
                        try {
                          result = remoteColl.updateOne(
                              localVersionInfo.getFilter(),
                              translatedUpdate
                          );
                        } catch (final StitchServiceException ex) {
                          action = SyncAction.DROP_EVENT_AND_PAUSE;
                          message = SyncMessage.EXCEPTION_UPDATE;
                          syncException = ex;
                          suppressLocalEvent = true;
                        }
                        if (action == null && result.getMatchedCount() == 0) {
                          // c. If no documents are matched and we haven't already registered
                          // an error condition, record that a conflict has occurred.
                          action = SyncAction.CONFLICT;
                          message = SyncMessage.VERSION_DIFFERENT_UPDATED_DOC_MESSAGE;
                        }
                      }
                    }
                    break;
                  }

                  case DELETE: {
                    nextVersion = null;
                    RemoteDeleteResult result = null;
                    // a. Delete the document in the remote database using a query for the _id and
                    // the version.
                    try {
                      result = remoteColl.deleteOne(localVersionInfo.getFilter());
                    } catch (final StitchServiceException ex) {
                      // b. If an error happens, report an error to the error listener.
                      action = SyncAction.DROP_EVENT_AND_PAUSE;
                      message = SyncMessage.EXCEPTION_DELETE;
                      syncException = ex;
                      suppressLocalEvent = true;
                    }
                    // c. If no documents are matched and we haven't already registered an error
                    // condition, record that a conflict has occurred.
                    if (action == null && result.getDeletedCount() == 0) {
                      remoteDocument = remoteColl.find(docFilter).first();
                      remoteDocumentFetched = true;
                      if (remoteDocument != null) {
                        action = SyncAction.CONFLICT;
                        message = SyncMessage.VERSION_DIFFERENT_DELETED_DOC_MESSAGE;
                      }
                    }

                    if (action == null) { // if we haven't encountered an error/conflict already
                      action = SyncAction.DELETE_LOCAL_DOC_AND_DESYNC;
                    }
                    break;
                  }

                  default:
                    action = SyncAction.DROP_EVENT_AND_PAUSE;
                    message = SyncMessage.UNKNOWN_OPTYPE_MESSAGE;
                    syncException = new Exception() {
                      public String toString() {
                        return localChangeEvent.getOperationType().toString();
                      }
                    };
                    suppressLocalEvent = true;
                }
              } else {
                nextVersion = null;
              }

              if (logger.isDebugEnabled()) {
                logger.debug(String.format(
                    Locale.US,
                    "t='%d': syncLocalToRemote ns=%s documentId=%s conflict=%s",
                    logicalT,
                    nsConfig.getNamespace(),
                    docConfig.getDocumentId(),
                    action == SyncAction.CONFLICT));
              }

              if (action != SyncAction.CONFLICT) {
                // iv. If no conflict has occurred, move on to the remote to local sync routine.

                // since we strip version information from documents before setting pending writes,
                // we don't have to worry about a stale document version in the event here.
                final ChangeEvent<BsonDocument> committedEvent =
                    docConfig.getLastUncommittedChangeEvent();
                if (!suppressLocalEvent) {
                  final ChangeEvent<BsonDocument> localEventToEmit =
                      committedEvent.withoutUncommittedWrites();

                  localSyncWriteModelContainer.addLocalChangeEvent(localEventToEmit);
                }

                // do this later before change is committed since it requires a write lock which we
                // cannot own while locking for read
                setPendingWritesComplete = true;
                if (committedEvent.getOperationType() != OperationType.DELETE) {
                  localSyncWriteModelContainer.addConfigWrite(
                      new ReplaceOneModel<>(CoreDocumentSynchronizationConfig.getDocFilter(
                          nsConfig.getNamespace(), docConfig.getDocumentId()),
                          docConfig));
                }
                remoteChangeEvent = null;
              } else {
                // v. Otherwise, invoke the collection-level conflict handler with the local change
                // event and the remote change event (synthesized by doing a lookup of the document
                // or sourced from the listener)
                if (!remoteDocumentFetched) {
                  remoteChangeEvent =
                      getSynthesizedRemoteChangeEventForDocument(remoteColl,
                          docConfig.getDocumentId());
                } else {
                  remoteChangeEvent =
                      getSynthesizedRemoteChangeEventForDocument(
                          remoteColl.getNamespace(),
                          docConfig.getDocumentId(),
                          remoteDocument);
                }
              }
            }
          } finally {
            docConfig.getLock().readLock().unlock();
          }

          if (setPendingWritesComplete) {
            docConfig.setPendingWritesComplete(
                HashUtils.hash(
                    sanitizeDocument(docConfig.getLastUncommittedChangeEvent().getFullDocument())
                ), nextVersion);
          }

          if (action != null) { // for now, since we do remote ops inline
            localSyncWriteModelContainer.merge(
                enqueueAction(nsConfig, docConfig, remoteChangeEvent, action, message,
                    SyncMessage.L2R_METHOD, syncException)
            );
          }
        }
        localSyncWriteModelContainer.commitAndClear();
      } finally {
        nsConfig.getLock().writeLock().unlock();
        streamerLock.writeLock().unlock();
      }
    }

    if (logger.isInfoEnabled()) {
      logger.info(String.format(
          Locale.US,
          "t='%d': syncLocalToRemote END",
          logicalT));
    }

    // 3. If there are still local writes pending for the document, it will go through the L2R
    //    phase on a subsequent pass and try to commit changes again.

  }

  private LocalSyncWriteModelContainer enqueueAction(
      @Nonnull  final NamespaceSynchronizationConfig nsConfig,
      @Nonnull  final CoreDocumentSynchronizationConfig docConfig,
      @Nullable final ChangeEvent<BsonDocument> remoteChangeEvent,
      @Nonnull  final SyncAction action,
      @Nonnull  final SyncMessage message,
      @Nonnull  final String caller,
      @Nullable final Exception ex) {
    String formattedMessage = null;
    if (message != null) {
      final String syncMessage = SyncMessage.constructMessage(action, message);
      if (ex != null) {
        formattedMessage = String.format(Locale.US, syncMessage, logicalT, caller,
            nsConfig.getNamespace(), docConfig.getDocumentId(), ex);
      } else {
        formattedMessage = String.format(Locale.US, syncMessage, logicalT, caller,
            nsConfig.getNamespace(), docConfig.getDocumentId());
      }
    }

    if (logger.isDebugEnabled()) {
      logger.debug(formattedMessage);
    }
    switch (action) {
      case DROP_EVENT:
      case WAIT:
        return null;
      case APPLY_AND_VERSION_FROM_REMOTE:
        final BsonDocument applyNewVersion =
            DocumentVersionInfo.getFreshVersionDocument();
        final LocalSyncWriteModelContainer writeContainer =
            replaceOrUpsertOneFromRemote(nsConfig, docConfig.getDocumentId(),
                remoteChangeEvent.getFullDocument(), applyNewVersion);

        writeContainer.addRemoteWrite(new ReplaceOneModel<>(
            getDocumentIdFilter(docConfig.getDocumentId()),
            withNewVersion(remoteChangeEvent.getFullDocument(), applyNewVersion)
        ));

        docConfig.setPendingWritesComplete(
            HashUtils.hash(sanitizeDocument(remoteChangeEvent.getFullDocument())),
            applyNewVersion
        );
        writeContainer.addConfigWrite(new ReplaceOneModel<>(
            CoreDocumentSynchronizationConfig.getDocFilter(
                nsConfig.getNamespace(), docConfig.getDocumentId()),
            docConfig
        ));

        return writeContainer;
      case APPLY_FROM_REMOTE:
        final BsonDocument remoteVersion =
            DocumentVersionInfo.getDocumentVersionDoc(remoteChangeEvent.getFullDocument());
        return replaceOrUpsertOneFromRemote(nsConfig, docConfig.getDocumentId(),
            remoteChangeEvent.getFullDocument(), remoteVersion);
      case CONFLICT:
        return resolveConflict(nsConfig, docConfig, remoteChangeEvent);
      case REMOTE_FIND:
        return remoteFind(nsConfig, docConfig, caller);
      case DROP_EVENT_AND_DESYNC:
        return emitErrorAndDesync(nsConfig, docConfig, formattedMessage, ex);
      case DROP_EVENT_AND_PAUSE:
        return emitErrorAndPause(docConfig, formattedMessage, ex);
      case DELETE_LOCAL_DOC:
        return deleteOneFromRemote(nsConfig, docConfig.getDocumentId());
      case DELETE_LOCAL_DOC_AND_DESYNC:
        return desyncDocumentsFromRemote(nsConfig, docConfig.getDocumentId())
            .withPostCommit(() -> triggerListeningToNamespace(nsConfig.getNamespace()));
      default:
        throw new IllegalStateException("unhandled synchronization action: " + action);
    }
  }

  private LocalSyncWriteModelContainer remoteFind(
      final NamespaceSynchronizationConfig nsConfig,
      final CoreDocumentSynchronizationConfig docConfig,
      final String caller) {
    final DocumentVersionInfo lastSeenVersionInfo =
        DocumentVersionInfo.getLocalVersionInfo(docConfig);

    SyncAction action = null;
    SyncMessage message = null;
    ChangeEvent<BsonDocument> remoteChangeEvent = null;

    // fetch the latest version to guard against stale events from other clients
    final BsonDocument newestRemoteDocument = this.getRemoteCollection(nsConfig.getNamespace())
        .find(new BsonDocument("_id", docConfig.getDocumentId())).first();

    final boolean isPendingWrite = docConfig.getLastUncommittedChangeEvent() != null;

    if (newestRemoteDocument == null) {
      // document was deleted remotely
      if (isPendingWrite) {
        action = SyncAction.CONFLICT;
        remoteChangeEvent = ChangeEvents.changeEventForLocalDelete(
            nsConfig.getNamespace(),
            docConfig.getDocumentId(),
            docConfig.hasUncommittedWrites());
      } else {
        action = SyncAction.DELETE_LOCAL_DOC_AND_DESYNC;
      }
      message = SyncMessage.REMOTE_FIND_DELETED_DOC_MESSAGE;
    } else {
      DocumentVersionInfo newestRemoteVersionInfo = null;
      try {
        newestRemoteVersionInfo = DocumentVersionInfo.getRemoteVersionInfo(newestRemoteDocument);
      } catch (final Exception e) {
        action = SyncAction.DROP_EVENT_AND_DESYNC;
        message = SyncMessage.CANNOT_PARSE_REMOTE_VERSION_MESSAGE;
      }

      if (newestRemoteVersionInfo != null) { // if no error has occurred
        final boolean newestRemoteHasNoVersion = !newestRemoteVersionInfo.hasVersion();
        final boolean lastSeenHasNoVersion = !lastSeenVersionInfo.hasVersion();

        @Nullable final DocumentVersionInfo.Version lastSeenVersion =
            lastSeenHasNoVersion ? null : lastSeenVersionInfo.getVersion();
        @Nullable final DocumentVersionInfo.Version newestRemoteVersion =
            newestRemoteHasNoVersion ? null : newestRemoteVersionInfo.getVersion();

        // if newest version is from us...
        if (!newestRemoteHasNoVersion && !lastSeenHasNoVersion
            && lastSeenVersion.getInstanceId().equals(newestRemoteVersion.getInstanceId())) {
          // since we believe we're behind, assume event is stale
          action = SyncAction.DROP_EVENT;
          message = SyncMessage.STALE_EVENT_MESSAGE;
        } else { // newest version is from somewhere else
          if (isPendingWrite) {
            action = SyncAction.CONFLICT;
          } else {
            action = SyncAction.APPLY_FROM_REMOTE;
          }
          message = SyncMessage.REMOTE_FIND_REPLACED_DOC_MESSAGE;
          remoteChangeEvent = ChangeEvents.changeEventForLocalReplace(
              nsConfig.getNamespace(),
              docConfig.getDocumentId(),
              newestRemoteDocument,
              docConfig.hasUncommittedWrites());
        }
      }
    }

    return enqueueAction(nsConfig, docConfig, remoteChangeEvent, action, message, caller, null);
  }

  private LocalSyncWriteModelContainer emitErrorAndPause(
      final CoreDocumentSynchronizationConfig docConfig,
      final String message,
      final Exception ex) {

    emitError(docConfig, message, ex);
    pauseDocument(docConfig);

    return null;
  }

  private LocalSyncWriteModelContainer emitErrorAndDesync(
      final NamespaceSynchronizationConfig nsConfig,
      final CoreDocumentSynchronizationConfig docConfig,
      final String message,
      final Exception ex) {

    emitError(docConfig, message, ex);

    return desyncDocumentsFromRemote(nsConfig, docConfig.getDocumentId());
  }

  private void emitError(final CoreDocumentSynchronizationConfig docConfig,
                         final String msg,
                         final Exception ex) {
    if (this.exceptionListener != null) {
      final Exception dispatchException;
      if (ex == null) {
        dispatchException = new DataSynchronizerException(msg);
      } else {
        dispatchException = ex;
      }
      this.dispatcher.dispatch(() -> {
        exceptionListener.onError(docConfig.getDocumentId(), dispatchException);
        return null;
      });
    }

    this.logger.error(msg);
  }

  private void pauseDocument(final CoreDocumentSynchronizationConfig docConfig) {
    this.logger.error(
        String.format("Pausing document %s", docConfig.getDocumentId()));
    docConfig.setPaused(true);
  }


  /**
   * Resolves a conflict between a synchronized document's local and remote state. The resolution
   * will result in either the document being desynchronized or being replaced with some resolved
   * state based on the conflict resolver specified for the document. Uses the last uncommitted
   * local event as the local state.
   *
   * @param nsConfig    the namespace synchronization config of the namespace where the document
   *                    lives.
   * @param docConfig   the configuration of the document that describes the resolver and current
   *                    state.
   * @param remoteEvent the remote change event that is conflicting.
   */
  @CheckReturnValue
  private LocalSyncWriteModelContainer resolveConflict(
      final NamespaceSynchronizationConfig nsConfig,
      final CoreDocumentSynchronizationConfig docConfig,
      final ChangeEvent<BsonDocument> remoteEvent
  ) {
    return resolveConflict(nsConfig, docConfig, docConfig.getLastUncommittedChangeEvent(),
        remoteEvent);
  }

  /**
   * Resolves a conflict between a synchronized document's local and remote state. The resolution
   * will result in either the document being desynchronized or being replaced with some resolved
   * state based on the conflict resolver specified for the document.
   *
   * @param nsConfig    the namespace synchronization config of the namespace where the document
   *                    lives.
   * @param docConfig   the configuration of the document that describes the resolver and current
   *                    state.
   * @param localEvent  the local change event that is conflicting.
   * @param remoteEvent the remote change event that is conflicting.
   */
  @CheckReturnValue
  private LocalSyncWriteModelContainer resolveConflict(
      final NamespaceSynchronizationConfig nsConfig,
      final CoreDocumentSynchronizationConfig docConfig,
      final ChangeEvent<BsonDocument> localEvent,
      final ChangeEvent<BsonDocument> remoteEvent
  ) {
    final MongoNamespace namespace = nsConfig.getNamespace();
    if (this.syncConfig.getNamespaceConfig(namespace).getConflictHandler() == null) {
      logger.warn(String.format(
          Locale.US,
          "t='%d': resolveConflict ns=%s documentId=%s no conflict resolver set; cannot "
                  + "resolve yet",
          logicalT,
          namespace,
          docConfig.getDocumentId()));
      return null;
    }

    // 2. Based on the result of the handler determine the next state of the document.
    final Object resolvedDocument;
    final ChangeEvent transformedRemoteEvent;
    try {
      final ChangeEvent transformedLocalEvent = ChangeEvents.transformChangeEventForUser(
          localEvent,
          syncConfig.getNamespaceConfig(namespace).getDocumentCodec());
      transformedRemoteEvent = ChangeEvents.transformChangeEventForUser(
              remoteEvent,
              syncConfig.getNamespaceConfig(namespace).getDocumentCodec());
      resolvedDocument = resolveConflictWithResolver(
          this.syncConfig.getNamespaceConfig(namespace).getConflictHandler(),
          docConfig.getDocumentId(),
          transformedLocalEvent,
          transformedRemoteEvent);
    } catch (final Exception ex) {
      pauseDocument(docConfig);
      emitError(docConfig,
          String.format(
              Locale.US,
              "t='%d': resolveConflict ns=%s documentId=%s resolution exception: %s",
              logicalT,
              namespace,
              docConfig.getDocumentId(),
              ex),
          ex);
      return null;
    }

    final BsonDocument remoteVersion;
    if (remoteEvent.getOperationType() == OperationType.DELETE) {
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
        emitError(docConfig,
            String.format(
                Locale.US,
                "t='%d': resolveConflict ns=%s documentId=%s got a remote "
                    + "document that could not have its version info parsed "
                    + "; dropping the event, and desyncing the document",
                logicalT,
                namespace,
                docConfig.getDocumentId()),
            null);
        return desyncDocumentsFromRemote(nsConfig, docConfig.getDocumentId());
      }
    }

    final boolean acceptRemote =
        (remoteEvent.getFullDocument() == null && resolvedDocument == null)
            || (remoteEvent.getFullDocument() != null
            && transformedRemoteEvent.getFullDocument().equals(resolvedDocument));

    // a. If the resolved document is null:
    if (resolvedDocument == null) {
      if (logger.isDebugEnabled()) {
        logger.debug(String.format(
            Locale.US,
            "t='%d': resolveConflict ns=%s documentId=%s deleting local and remote with remote "
                + "version acknowledged",
            logicalT,
            namespace,
            docConfig.getDocumentId()));
      }

      if (acceptRemote) {
        // i. If the remote event was a DELETE, delete the document locally, desynchronize the
        //    document, and emit a change event for the deletion.
        return deleteOneFromRemote(nsConfig, docConfig.getDocumentId());
      } else {
        // ii. Otherwise, delete the document locally, mark that there are pending writes for this
        //     document, and emit a change event for the deletion.
        return deleteOneFromResolution(nsConfig, docConfig.getDocumentId(), remoteVersion);
      }
    } else {
      // b. If the resolved document is not null:

      // Update the document locally which will keep the pending writes but with
      // a new version next time around.
      @SuppressWarnings("unchecked") final BsonDocument docForStorage =
          BsonUtils.documentToBsonDocument(
              resolvedDocument,
              syncConfig.getNamespaceConfig(namespace).getDocumentCodec());

      if (logger.isDebugEnabled()) {
        logger.debug(String.format(
            Locale.US,
            "t='%d': resolveConflict ns=%s documentId=%s replacing local with resolved document "
                + "with remote version acknowledged: %s",
            logicalT,
            namespace,
            docConfig.getDocumentId(),
            docForStorage.toJson()));
      }
      if (acceptRemote) {
        // i. If the remote document is equal to the resolved document, replace the document
        //    locally, mark the document as having no pending writes, and emit a REPLACE change
        //    event if the document had not existed prior, or UPDATE if it had.
        return replaceOrUpsertOneFromRemote(nsConfig, docConfig.getDocumentId(), docForStorage,
            remoteVersion);
      } else {
        // ii. Otherwise, replace the local document with the resolved document locally, mark that
        //     there are pending writes for this document, and emit an UPDATE change event, or a
        //     DELETE change event (if the remoteEvent's operation type was DELETE).
        return updateOrUpsertOneFromResolution(
            nsConfig,
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
      return ChangeEvents.changeEventForLocalDelete(ns, documentId, false);
    }
    return ChangeEvents.changeEventForLocalReplace(ns, documentId, document, false);
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
    this.waitUntilInitialized();
    try {
      ongoingOperationsGroup.enter();
      return this.syncConfig.getSynchronizedNamespaces();
    } finally {
      ongoingOperationsGroup.exit();
    }
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
    this.waitUntilInitialized();
    try {
      ongoingOperationsGroup.enter();
      return this.syncConfig.getSynchronizedDocuments(namespace);
    } finally {
      ongoingOperationsGroup.exit();
    }
  }

  /**
   * Returns the set of synchronized documents _ids in a namespace.
   *
   * @param namespace the namespace to get synchronized documents _ids for.
   * @return the set of synchronized documents _ids in a namespace.
   */
  Set<BsonValue> getSynchronizedDocumentIds(final MongoNamespace namespace) {
    this.waitUntilInitialized();
    try {
      ongoingOperationsGroup.enter();
      return this.syncConfig.getSynchronizedDocumentIds(namespace);
    } finally {
      ongoingOperationsGroup.exit();
    }
  }

  /**
   * Return the set of synchronized document _ids in a namespace
   * that have been paused due to an irrecoverable error.
   *
   * @param namespace the namespace to get paused document _ids for.
   * @return the set of paused document _ids in a namespace
   */
  public Set<BsonValue> getPausedDocumentIds(final MongoNamespace namespace) {
    this.waitUntilInitialized();

    try {
      ongoingOperationsGroup.enter();
      final Set<BsonValue> pausedDocumentIds = new HashSet<>();

      for (final CoreDocumentSynchronizationConfig config :
          this.syncConfig.getSynchronizedDocuments(namespace)) {
        if (config.isPaused()) {
          pausedDocumentIds.add(config.getDocumentId());
        }
      }

      return pausedDocumentIds;
    } finally {
      ongoingOperationsGroup.exit();
    }
  }

  /**
   * Requests that a document be synchronized by the given _id. Actual synchronization of the
   * document will happen later in a {@link DataSynchronizer#doSyncPass()} iteration.
   *
   * @param namespace  the namespace to put the document in.
   * @param documentIds the _ids of the documents.
   */
  public void syncDocumentsFromRemote(
      final MongoNamespace namespace,
      final BsonValue... documentIds
  ) {
    this.waitUntilInitialized();

    try {
      ongoingOperationsGroup.enter();

      if (syncConfig.addSynchronizedDocuments(namespace, documentIds)) {
        triggerListeningToNamespace(namespace);
      }
    } finally {
      ongoingOperationsGroup.exit();
    }
  }

  void desyncMany(final MongoNamespace namespace, final BsonValue... documentIds) {
    final NamespaceSynchronizationConfig nsConfig = syncConfig.getNamespaceConfig(namespace);
    final LocalSyncWriteModelContainer localSyncWriteModelContainer =
        this.desyncDocumentsFromRemote(nsConfig, documentIds);
    if (localSyncWriteModelContainer != null) {
      localSyncWriteModelContainer.commitAndClear();
    }
  }

  /**
   * Requests that a document be no longer be synchronized by the given _id. Any uncommitted writes
   * will be lost.
   *
   * @param nsConfig    the namespace synchronization config of the namespace where the document
   *                    lives.
   * @param documentIds the _ids of the documents.
   */
  @CheckReturnValue
  LocalSyncWriteModelContainer desyncDocumentsFromRemote(
      final NamespaceSynchronizationConfig nsConfig,
      final BsonValue... documentIds) {
    this.waitUntilInitialized();
    final MongoNamespace namespace = nsConfig.getNamespace();
    final Lock lock = this.syncConfig.getNamespaceConfig(namespace).getLock().writeLock();
    lock.lock();
    final DeleteManyModel<CoreDocumentSynchronizationConfig> configsToDelete;
    try {
      ongoingOperationsGroup.enter();
      configsToDelete = syncConfig.removeSynchronizedDocuments(namespace, documentIds);
    } finally {
      lock.unlock();
      ongoingOperationsGroup.exit();
    }

    LocalSyncWriteModelContainer container = null;
    if (configsToDelete != null) {
      container = newWriteModelContainer(nsConfig);
      container.addDocIDs(documentIds);
      container.addLocalWrite(new DeleteManyModel<>(
          new BsonDocument("_id",
              new BsonDocument("$in", new BsonArray(Arrays.asList(documentIds))))
      ));
      container.addConfigWrite(configsToDelete);
    }

    return container;
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
    this.waitUntilInitialized();

    try {
      ongoingOperationsGroup.enter();
      return getLocalCollection(namespace).countDocuments(filter, options);
    } finally {
      ongoingOperationsGroup.exit();
    }
  }

  public <T> T findOne(
          final MongoNamespace namespace,
          final BsonDocument filter,
          final BsonDocument projection,
          final BsonDocument sort,
          final Class<T> resultClass,
          final CodecRegistry codecRegistry
  ) {
    this.waitUntilInitialized();

    ongoingOperationsGroup.enter();
    final Lock lock = this.syncConfig.getNamespaceConfig(namespace).getLock().writeLock();
    lock.lock();
    try {
      return getLocalCollection(namespace, resultClass, codecRegistry)
              .find(filter)
              .limit(1)
              .projection(projection)
              .sort(sort)
              .first();
    } finally {
      lock.unlock();
      ongoingOperationsGroup.exit();
    }
  }

  Collection<BsonDocument> find(
      final MongoNamespace namespace,
      final BsonDocument filter
  ) {
    this.waitUntilInitialized();

    ongoingOperationsGroup.enter();
    try {
      return getLocalCollection(namespace)
          .find(filter)
          .into(new ArrayList<>());
    } finally {
      ongoingOperationsGroup.exit();
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
    this.waitUntilInitialized();

    ongoingOperationsGroup.enter();
    try {
      return getLocalCollection(namespace, resultClass, codecRegistry)
          .find(filter)
          .limit(limit)
          .projection(projection)
          .sort(sort)
          .into(new ArrayList<>());
    } finally {
      ongoingOperationsGroup.exit();
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
    this.waitUntilInitialized();

    ongoingOperationsGroup.enter();
    try {
      return getLocalCollection(namespace).aggregate(pipeline, resultClass);
    } finally {
      ongoingOperationsGroup.exit();
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
    this.waitUntilInitialized();

    try {
      ongoingOperationsGroup.enter();
      // Remove forbidden fields from the document before inserting it into the local collection.
      final BsonDocument docForStorage = sanitizeDocument(document);

      final NamespaceSynchronizationConfig nsConfig =
          this.syncConfig.getNamespaceConfig(namespace);
      final Lock lock = nsConfig.getLock().writeLock();
      lock.lock();
      final ChangeEvent<BsonDocument> event;
      final BsonValue documentId;
      try {
        getLocalCollection(namespace).insertOne(docForStorage);
        documentId = BsonUtils.getDocumentId(docForStorage);
        event = ChangeEvents.changeEventForLocalInsert(namespace, docForStorage, true);
        final CoreDocumentSynchronizationConfig config = syncConfig.addAndGetSynchronizedDocument(
            namespace,
            documentId
        );
        config.setSomePendingWritesAndSave(logicalT, event);
      } finally {
        lock.unlock();
      }
      triggerListeningToNamespace(namespace);
      eventDispatcher.emitEvent(nsConfig, event);
    } finally {
      ongoingOperationsGroup.exit();
    }
  }

  /**
   * Inserts one or more documents.
   *
   * @param documents the documents to insert
   */
  void insertMany(final MongoNamespace namespace,
                  final List<BsonDocument> documents) {
    this.waitUntilInitialized();

    try {
      ongoingOperationsGroup.enter();
      // Remove forbidden fields from the documents before inserting them into the local collection.
      final List<BsonDocument> docsForStorage = new ArrayList<>(documents.size());

      for (final BsonDocument document : documents) {
        docsForStorage.add(sanitizeDocument(document));
      }

      final NamespaceSynchronizationConfig nsConfig = this.syncConfig.getNamespaceConfig(namespace);
      final Lock lock = nsConfig.getLock().writeLock();
      lock.lock();
      final List<ChangeEvent<BsonDocument>> eventsToEmit = new ArrayList<>();
      try {
        getLocalCollection(namespace).insertMany(docsForStorage);
        for (final BsonDocument document : docsForStorage) {
          final BsonValue documentId = BsonUtils.getDocumentId(document);
          final ChangeEvent<BsonDocument> event =
              ChangeEvents.changeEventForLocalInsert(namespace, document, true);
          final CoreDocumentSynchronizationConfig config = syncConfig.addAndGetSynchronizedDocument(
              namespace,
              documentId
          );
          config.setSomePendingWritesAndSave(logicalT, event);
          eventsToEmit.add(event);
        }
      } finally {
        lock.unlock();
      }
      triggerListeningToNamespace(namespace);
      for (final ChangeEvent<BsonDocument> event : eventsToEmit) {
        eventDispatcher.emitEvent(nsConfig, event);
      }
    } finally {
      ongoingOperationsGroup.exit();
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
    this.waitUntilInitialized();

    try {
      ongoingOperationsGroup.enter();
      final NamespaceSynchronizationConfig nsConfig = this.syncConfig.getNamespaceConfig(namespace);
      final Lock lock = nsConfig.getLock().writeLock();
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

        // Ensure that the update didn't add any forbidden fields to the document, and remove them
        // if it did.
        final BsonDocument documentAfterUpdate =
            sanitizeCachedDocument(localCollection, unsanitizedDocumentAfterUpdate, documentId);

        // if there was no document prior and this was an upsert,
        // treat this as an insert.
        // else this is an update
        if (documentBeforeUpdate == null && updateOptions.isUpsert()) {
          triggerNamespace = true;
          config = syncConfig.addAndGetSynchronizedDocument(namespace, documentId);
          event = ChangeEvents.changeEventForLocalInsert(namespace, documentAfterUpdate, true);
        } else {
          triggerNamespace = false;
          config = syncConfig.getSynchronizedDocument(namespace, documentId);
          event = ChangeEvents.changeEventForLocalUpdate(
              namespace,
              BsonUtils.getDocumentId(documentAfterUpdate),
              UpdateDescription.diff(documentBeforeUpdate, documentAfterUpdate),
              documentAfterUpdate,
              true);
        }

        config.setSomePendingWritesAndSave(logicalT, event);

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
      eventDispatcher.emitEvent(nsConfig, event);
      return UpdateResult.acknowledged(1, 1L, updateOptions.isUpsert() ? documentId : null);
    } finally {
      ongoingOperationsGroup.exit();
    }
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
    this.waitUntilInitialized();

    ongoingOperationsGroup.enter();
    try {
      final List<ChangeEvent<BsonDocument>> eventsToEmit = new ArrayList<>();
      final UpdateResult result;
      final NamespaceSynchronizationConfig nsConfig = this.syncConfig.getNamespaceConfig(namespace);
      final Lock lock = nsConfig.getLock().writeLock();
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
                config = syncConfig.addAndGetSynchronizedDocument(namespace, documentId);
                event = ChangeEvents.changeEventForLocalInsert(namespace, afterDocument, true);
              } else {
                config = syncConfig.getSynchronizedDocument(namespace, documentId);
                event = ChangeEvents.changeEventForLocalUpdate(
                    namespace,
                    documentId,
                    UpdateDescription.diff(beforeDocument, afterDocument),
                    afterDocument,
                    true);
              }

              config.setSomePendingWritesAndSave(logicalT, event);
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
        eventDispatcher.emitEvent(nsConfig, event);
      }
      return result;
    } finally {
      ongoingOperationsGroup.exit();
    }
  }

  /**
   * Replaces a single synchronized document by its given id with the given full document
   * replacement. No replacement will occur if the _id is not being synchronized.
   *
   * @param nsConfig   the namespace sync configuration of the namespace where the document lives.
   * @param documentId the _id of the document.
   * @param document   the replacement document.
   */
  @CheckReturnValue
  private LocalSyncWriteModelContainer updateOrUpsertOneFromResolution(
      final NamespaceSynchronizationConfig nsConfig,
      final BsonValue documentId,
      final BsonDocument document,
      final BsonDocument atVersion,
      final ChangeEvent<BsonDocument> remoteEvent
  ) {
    final MongoNamespace namespace = nsConfig.getNamespace();
    final ChangeEvent<BsonDocument> event;
    final Lock lock =
        this.syncConfig.getNamespaceConfig(namespace).getLock().writeLock();
    lock.lock();
    final CoreDocumentSynchronizationConfig config;
    final BsonDocument docForStorage;
    try {
      config =
          syncConfig.getSynchronizedDocument(namespace, documentId);
      if (config == null) {
        return null;
      }

      // Remove forbidden fields from the resolved document before it will updated/upserted in the
      // local collection.
      docForStorage = sanitizeDocument(document);

      if (document.get("_id") == null && remoteEvent.getDocumentKey().get("_id") != null) {
        document.put("_id", remoteEvent.getDocumentKey().get("_id"));
      }

      if (remoteEvent.getOperationType() == OperationType.DELETE) {
        event = ChangeEvents.changeEventForLocalInsert(namespace, docForStorage, true);
      } else {
        event = ChangeEvents.changeEventForLocalUpdate(
            namespace,
            documentId,
            UpdateDescription.diff(
                    sanitizeDocument(remoteEvent.getFullDocument()),
                    docForStorage),
            docForStorage,
            true);
      }
    } finally {
      lock.unlock();
    }
    config.setSomePendingWrites(
        logicalT,
        atVersion,
        HashUtils.hash(docForStorage),
        event);
    eventDispatcher.emitEvent(nsConfig, event);
    final LocalSyncWriteModelContainer syncWriteModelContainer = newWriteModelContainer(nsConfig);

    syncWriteModelContainer.addDocIDs(documentId);
    syncWriteModelContainer.addLocalWrite(
        new ReplaceOneModel<>(getDocumentIdFilter(documentId), docForStorage,
            new ReplaceOptions().upsert(true)));
    syncWriteModelContainer.addConfigWrite(
        new ReplaceOneModel<>(CoreDocumentSynchronizationConfig.getDocFilter(
            namespace, config.getDocumentId()), config));

    return syncWriteModelContainer;
  }

  /**
   * Replaces a single synchronized document by its given id with the given full document
   * replacement. No replacement will occur if the _id is not being synchronized.
   *
   * @param nsConfig  the namespace synchronization config of the namespace where the document
   *                  lives.
   * @param documentId the _id of the document.
   * @param remoteDocument   the replacement document.
   */
  @CheckReturnValue
  private LocalSyncWriteModelContainer replaceOrUpsertOneFromRemote(
      final NamespaceSynchronizationConfig nsConfig,
      final BsonValue documentId,
      final BsonDocument remoteDocument,
      final BsonDocument atVersion
  ) {
    final MongoNamespace namespace = nsConfig.getNamespace();
    final ChangeEvent<BsonDocument> event;
    final Lock lock =
        this.syncConfig.getNamespaceConfig(namespace).getLock().writeLock();
    lock.lock();
    final BsonDocument docForStorage;
    final CoreDocumentSynchronizationConfig config;
    try {
      config = syncConfig.getSynchronizedDocument(namespace, documentId);
      if (config == null) {
        return null;
      }

      docForStorage = sanitizeDocument(remoteDocument);

      config.setPendingWritesComplete(HashUtils.hash(docForStorage), atVersion);

      event = ChangeEvents.changeEventForLocalReplace(namespace, documentId, docForStorage, false);
    } finally {
      lock.unlock();
    }

    eventDispatcher.emitEvent(nsConfig, event);
    final LocalSyncWriteModelContainer container = newWriteModelContainer(nsConfig);

    container.addDocIDs(documentId);
    container.addLocalWrite(new ReplaceOneModel<>(
        getDocumentIdFilter(documentId),
        docForStorage,
        new ReplaceOptions().upsert(true)));
    container.addConfigWrite(new ReplaceOneModel<>(
        CoreDocumentSynchronizationConfig.getDocFilter(namespace, config.getDocumentId()
        ), config));

    return container;
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
    this.waitUntilInitialized();

    try {
      ongoingOperationsGroup.enter();
      final ChangeEvent<BsonDocument> event;
      final DeleteResult result;
      final NamespaceSynchronizationConfig nsConfig =
          this.syncConfig.getNamespaceConfig(namespace);
      nsConfig.getLock().writeLock().lock();
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
        event = ChangeEvents.changeEventForLocalDelete(namespace, documentId, true);

        // this block is to trigger coalescence for a delete after insert
        if (config.getLastUncommittedChangeEvent() != null
            && config.getLastUncommittedChangeEvent().getOperationType()
            == OperationType.INSERT) {
          final LocalSyncWriteModelContainer localSyncWriteModelContainer =
              desyncDocumentsFromRemote(nsConfig, config.getDocumentId());
          localSyncWriteModelContainer.commitAndClear();
          triggerListeningToNamespace(namespace);
          undoCollection.deleteOne(getDocumentIdFilter(config.getDocumentId()));
          return result;
        }

        config.setSomePendingWritesAndSave(logicalT, event);
        undoCollection.deleteOne(getDocumentIdFilter(config.getDocumentId()));
      } finally {
        nsConfig.getLock().writeLock().unlock();
      }
      eventDispatcher.emitEvent(nsConfig, event);
      return result;
    } finally {
      ongoingOperationsGroup.exit();
    }
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
    this.waitUntilInitialized();

    try {
      ongoingOperationsGroup.enter();
      final List<ChangeEvent<BsonDocument>> eventsToEmit = new ArrayList<>();
      final DeleteResult result;
      final NamespaceSynchronizationConfig nsConfig = this.syncConfig.getNamespaceConfig(namespace);
      final Lock lock = nsConfig.getLock().writeLock();
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
              ChangeEvents.changeEventForLocalDelete(namespace, documentId, true);

          // this block is to trigger coalescence for a delete after insert
          if (config.getLastUncommittedChangeEvent() != null
              && config.getLastUncommittedChangeEvent().getOperationType()
              == OperationType.INSERT) {
            desyncDocumentsFromRemote(nsConfig, config.getDocumentId())
                .commitAndClear();
            undoCollection.deleteOne(getDocumentIdFilter(documentId));
            continue;
          }

          config.setSomePendingWritesAndSave(logicalT, event);
          undoCollection.deleteOne(getDocumentIdFilter(documentId));
          eventsToEmit.add(event);
        }
        triggerListeningToNamespace(namespace);
      } finally {
        lock.unlock();
      }
      for (final ChangeEvent<BsonDocument> event : eventsToEmit) {
        eventDispatcher.emitEvent(nsConfig, event);
      }
      return result;
    } finally {
      ongoingOperationsGroup.exit();
    }
  }

  /**
   * Deletes a single synchronized document by its given id. No deletion will occur if the _id is
   * not being synchronized.
   *
   * @param nsConfig   the namespace synchronization config of the namespace where the document
   *                   lives.
   * @param documentId the _id of the document.
   */
  @CheckReturnValue
  private @Nullable
  LocalSyncWriteModelContainer deleteOneFromResolution(
      final NamespaceSynchronizationConfig nsConfig,
      final BsonValue documentId,
      final BsonDocument atVersion
  ) {
    final MongoNamespace namespace = nsConfig.getNamespace();
    final ChangeEvent<BsonDocument> event;
    final Lock lock =
        this.syncConfig.getNamespaceConfig(namespace).getLock().writeLock();
    lock.lock();
    final CoreDocumentSynchronizationConfig config;
    try {
      config = syncConfig.getSynchronizedDocument(namespace, documentId);
      if (config == null) {
        return null;
      }

      event = ChangeEvents.changeEventForLocalDelete(namespace, documentId, true);
      config.setSomePendingWrites(logicalT, atVersion, 0L, event);
    } finally {
      lock.unlock();
    }

    eventDispatcher.emitEvent(nsConfig, event);
    final LocalSyncWriteModelContainer container = newWriteModelContainer(nsConfig);

    container.addDocIDs(documentId);
    container.addLocalWrite(new DeleteOneModel<>(getDocumentIdFilter(documentId)));
    container.addConfigWrite(
        new ReplaceOneModel<>(CoreDocumentSynchronizationConfig.getDocFilter(
          namespace, config.getDocumentId()
        ), config));

    return container;
  }

  private LocalSyncWriteModelContainer newWriteModelContainer(
      final NamespaceSynchronizationConfig nsConfig
  ) {
    final MongoNamespace namespace = nsConfig.getNamespace();
    return new LocalSyncWriteModelContainer(
        nsConfig,
        getLocalCollection(namespace),
        getRemoteCollection(namespace),
        getUndoCollection(namespace),
        eventDispatcher
    );
  }

  /**
   * Deletes a single synchronized document by its given id. No deletion will occur if the _id is
   * not being synchronized.
   *
   * @param nsConfig   the namespace synchronization config of the namespace where the document
   *                   lives.
   * @param documentId the _id of the document.
   */
  @CheckReturnValue
  @Nullable
  private LocalSyncWriteModelContainer deleteOneFromRemote(
      final NamespaceSynchronizationConfig nsConfig,
      final BsonValue documentId
  ) {
    final MongoNamespace namespace = nsConfig.getNamespace();
    final Lock lock = this.syncConfig.getNamespaceConfig(namespace).getLock().writeLock();
    lock.lock();
    final CoreDocumentSynchronizationConfig config;
    try {
      config = syncConfig.getSynchronizedDocument(namespace, documentId);
      if (config == null) {
        return null;
      }
    } finally {
      lock.unlock();
    }
    eventDispatcher.emitEvent(nsConfig,
        ChangeEvents.changeEventForLocalDelete(namespace, documentId, false));
    return desyncDocumentsFromRemote(nsConfig, documentId);
  }

  private void triggerListeningToNamespace(final MongoNamespace namespace) {
    syncLock.lock();
    try {
      final NamespaceSynchronizationConfig nsConfig = this.syncConfig.getNamespaceConfig(namespace);
      if (nsConfig.getSynchronizedDocuments().isEmpty()) {
        instanceChangeStreamListener.removeNamespace(namespace);
        return;
      }
      if (!nsConfig.isConfigured()) {
        return;
      }
      if (nsConfig.getSyncFrequency().getType() == SyncFrequencyType.ON_DEMAND) {
        return;
      }
      if (nsConfig.getSyncFrequency().getType() == SyncFrequencyType.SCHEDULED) {
        if (!((Scheduled) nsConfig.getSyncFrequency()).isConnected()) {
          return;
        }
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

  // ----- Utilities -----

  /**
   * Returns the undo collection representing the given namespace for recording documents that
   * may need to be reverted after a system failure.
   *
   * @param namespace the namespace referring to the undo collection.
   * @return the undo collection representing the given namespace for recording documents that
   * may need to be reverted after a system failure.
   */
  MongoCollection<BsonDocument> getUndoCollection(final MongoNamespace namespace) {
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
  MongoCollection<BsonDocument> getLocalCollection(final MongoNamespace namespace) {
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
    Collections.addAll(ids, staleIds.toArray(new BsonValue[0]));

    if (ids.size() == 0) {
      return new HashSet<>();
    }

    return this.getRemoteCollection(nsConfig.getNamespace()).find(
        new BsonDocument("_id", new BsonDocument("$in", ids))
    ).into(new HashSet<>());
  }

  void waitUntilInitialized() {
    try {
      this.initThread.join();
    } catch (InterruptedException e) {
      throw new StitchClientException(StitchClientErrorCode.COULD_NOT_LOAD_DATA_SYNCHRONIZER);
    }
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

  private enum SyncMessage {
    APPLY_FROM_REMOTE_MESSAGE("replacing local with remote document with new version "
        + "as there are no local pending writes"),
    CANNOT_PARSE_REMOTE_VERSION_MESSAGE("got a remote document that could not have its version "
        + "info parsed"),
    DELETE_FROM_REMOTE_MESSAGE("deleting local as there are no local pending writes"),
    DUPLICATE_KEY_EXCEPTION_MESSAGE("duplicate key exception on insert"),
    EMPTY_VERSION_MESSAGE("remote or local have an empty version"),
    EMPTY_UPDATE_DESCRIPTION("local change event update description is empty for UPDATE"),
    EXCEPTION_DELETE("exception on delete: %s"),
    EXCEPTION_INSERT("exception on insert: %s"),
    EXCEPTION_REPLACE("exception on replace: %s"),
    EXCEPTION_UPDATE("exception on update: %s"),
    EXPECTED_LOCAL_DOCUMENT_TO_EXIST_MESSAGE("expected document to exist for local update change "
        + "event"),
    INSTANCE_ID_MISMATCH_MESSAGE("remote event created by different device from last seen event"),
    PENDING_WRITE_DELETE_MESSAGE("remote delete but a write is pending"),
    PENDING_WRITE_EMPTY_VERSION_MESSAGE("remote or local have an empty version but a write is "
        + "pending"),
    PROBABLY_GENERATED_BY_US_MESSAGE("remote change event was generated by us"),
    REMOTE_FIND_DELETED_DOC_MESSAGE("remote event generated by a different client and latest "
        + "document lookup indicates a remote delete occurred"),
    REMOTE_FIND_REPLACED_DOC_MESSAGE("latest document lookup indicates a remote replace occurred"),
    REMOTE_UPDATE_WITHOUT_VERSION_MESSAGE("remote document changed but version was unmodified"),
    SIMULTANEOUS_WRITES_MESSAGE("has multiple events at same logical time"),
    STALE_LOCAL_WRITE_MESSAGE("remote event version has higher counter than local pending write"),
    STALE_EVENT_MESSAGE("remote change event is stale"),
    STALE_PROTOCOL_VERSION_MESSAGE("last seen change event has an unsupported synchronization"
        + " protocol version %s"),
    UNKNOWN_OPTYPE_MESSAGE("unknown operation type: %s"),
    UNKNOWN_REMOTE_PROTOCOL_VERSION_MESSAGE("got a remote document with an unsupported "
        + "synchronization protocol version %s"),
    VERSION_DIFFERENT_DELETED_DOC_MESSAGE("version different on removed document"),
    VERSION_DIFFERENT_REPLACED_DOC_MESSAGE("version different on replaced document or document was "
        + "deleted"),
    VERSION_DIFFERENT_UNPROCESSED_EVENT_MESSAGE("version different on unprocessed change event for "
        + "document"),
    VERSION_DIFFERENT_UPDATED_DOC_MESSAGE("version different on updated document or document was "
        + "deleted");


    static final String BASE_MESSAGE = "t='%d': %s ns=%s documentId=%s ";
    static final String R2L_METHOD = "syncRemoteChangeEventToLocal";
    static final String L2R_METHOD = "syncLocalToRemote";

    final String message;

    SyncMessage(final String message) {
      this.message = BASE_MESSAGE.concat(message);
    }

    static String constructMessage(
        final SyncAction action,
        final SyncMessage message) {
      return action.applyDescription(message);
    }

    @Override
    public String toString() {
      return this.message;
    }
  }

  private enum SyncAction {
    APPLY_FROM_REMOTE("; applying changes from the remote document"),
    APPLY_AND_VERSION_FROM_REMOTE("; applying changes from the remote document"),
    CONFLICT("; raising conflict"),
    DELETE_LOCAL_DOC_AND_DESYNC("; deleting and desyncing the document"),
    DELETE_LOCAL_DOC("; applying the remote delete"),
    DROP_EVENT("; dropping the event"),
    DROP_EVENT_AND_PAUSE("; dropping the event and pausing the document"),
    DROP_EVENT_AND_DESYNC("; dropping the event and desyncing the document"),
    WAIT("; waiting until next pass"),
    REMOTE_FIND("; re-checking against remote collection");

    private final String description;

    SyncAction(final String description) {
      this.description = description;
    }

    public String applyDescription(final SyncMessage message) {
      return message.toString().concat(description);
    }
  }
}
