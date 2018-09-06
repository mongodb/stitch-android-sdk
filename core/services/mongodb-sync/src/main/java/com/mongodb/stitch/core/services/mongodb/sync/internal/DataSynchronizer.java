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

package com.mongodb.stitch.core.services.mongodb.sync.internal;

import static com.mongodb.stitch.core.services.mongodb.sync.internal.ChangeEvent.changeEventForLocalDelete;
import static com.mongodb.stitch.core.services.mongodb.sync.internal.ChangeEvent.changeEventForLocalInsert;
import static com.mongodb.stitch.core.services.mongodb.sync.internal.ChangeEvent.changeEventForLocalReplace;
import static com.mongodb.stitch.core.services.mongodb.sync.internal.ChangeEvent.changeEventForLocalUpdate;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoNamespace;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndReplaceOptions;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.stitch.core.StitchServiceErrorCode;
import com.mongodb.stitch.core.StitchServiceException;
import com.mongodb.stitch.core.internal.common.AuthMonitor;
import com.mongodb.stitch.core.internal.common.BsonUtils;
import com.mongodb.stitch.core.internal.common.Dispatcher;
import com.mongodb.stitch.core.internal.net.NetworkMonitor;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoClient;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoCollection;
import com.mongodb.stitch.core.services.mongodb.sync.ChangeEventListener;
import com.mongodb.stitch.core.services.mongodb.sync.SyncConflictResolver;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.diagnostics.Logger;
import org.bson.diagnostics.Loggers;

/**
 * DataSynchronizer handles the bidirectional synchronization of documents between a local MongoDB
 * and a remote MongoDB (via Stitch). It also expose CRUD operations to interact with synchronized
 * documents.
 */
// TODO: Logging into a different user can produce odd results based on rules.
// TODO: Threading model okay?
// TODO: implement unwatch
// TODO: filter out and forbid usage of version ids outside of here
// TODO: findOneById with filter
// TODO: Test delete/delete insert/insert update/update etc...
// TODO: StitchReachabilityMonitor for when Stitch goes down and we can gracefully fail and give
// you local only results.
public class DataSynchronizer {

  static final String DOCUMENT_VERSION_FIELD = "__stitch_sync_version";

  private final CoreStitchServiceClient service;
  private final MongoClient localClient;
  private final CoreRemoteMongoClient remoteClient;
  private final NetworkMonitor networkMonitor;
  private final AuthMonitor authMonitor;
  private final Logger logger;
  private final MongoDatabase configDb;
  private final MongoCollection<InstanceSynchronizationConfig> instancesColl;
  private final Lock syncLock;
  private InstanceChangeStreamListener instanceChangeStreamListener;

  private InstanceSynchronizationConfig syncConfig;
  private Thread syncThread;
  private long logicalT = 0; // The current logical time or sync iteration.

  private final Map<MongoNamespace, Map<BsonValue, DocumentListenerConfig>> nsEventListeners;
  private final Lock listenersLock;
  private final Dispatcher eventDispatcher;

  DataSynchronizer(
      final String instanceKey,
      final CoreStitchServiceClient service,
      final MongoClient localClient,
      final CoreRemoteMongoClient remoteClient,
      final NetworkMonitor networkMonitor,
      final AuthMonitor authMonitor
  ) {
    this.service = service;
    this.localClient = localClient;
    this.remoteClient = remoteClient;
    this.networkMonitor = networkMonitor;
    this.authMonitor = authMonitor;
    this.syncLock = new ReentrantLock();
    this.nsEventListeners = new HashMap<>();
    this.listenersLock = new ReentrantLock();
    this.eventDispatcher = new Dispatcher();

    // TODO: add back after SERVER-35421
    // final MongoDatabase configDb = localClient.getDatabase("sync_config");
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

    if (instancesColl.count() != 0) {
      this.syncConfig = new InstanceSynchronizationConfig(
          configDb,
          instancesColl);
      instancesColl.insertOne(this.syncConfig);
    } else {
      this.syncConfig = new InstanceSynchronizationConfig(
          configDb,
          instancesColl,
          instancesColl.find().first());
    }
    this.instanceChangeStreamListener = new InstanceChangeStreamShortPoller(
        syncConfig,
        service,
        networkMonitor,
        authMonitor);
    for (final MongoNamespace ns : this.syncConfig.getSynchronizedNamespaces()) {
      this.instanceChangeStreamListener.addNamespace(ns);
    }
    this.instanceChangeStreamListener.start();

    this.logger =
        Loggers.getLogger(String.format("DataSynchronizer-%s", instanceKey));
  }

  /**
   * Reloads the synchronization config. This wipes all in-memory synchronization settings.
   */
  public void reloadConfig() {
    syncLock.lock();
    try {
      this.instanceChangeStreamListener.stop();
      this.syncConfig = new InstanceSynchronizationConfig(
          configDb,
          instancesColl,
          instancesColl.find().first());
      this.instanceChangeStreamListener = new InstanceChangeStreamShortPoller(
          syncConfig,
          service,
          networkMonitor,
          authMonitor);
    } finally {
      syncLock.unlock();
    }
  }

  /**
   * Starts data synchronization in a background thread.
   */
  public void start() {
    syncLock.lock();
    try {
      if (syncThread != null) {
        return;
      }
      instanceChangeStreamListener.start();
      syncThread = new Thread(new DataSynchronizerRunner(
          new WeakReference<>(this),
          networkMonitor,
          logger));
      syncThread.start();
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
      this.eventDispatcher.close();
      stop();
      this.localClient.close();
    } finally {
      syncLock.unlock();
    }
  }

  // ---- Core Synchronization Logic -----

  private boolean localToRemoteFirst = false;

  /**
   * Swaps which sync direction comes first. Note: this should only be used for testing purposes.
   *
   * @param localToRemoteFirst whether or not L2R should go first.
   */
  public void swapSyncDirection(final boolean localToRemoteFirst) {
    syncLock.lock();
    try {
      this.localToRemoteFirst = localToRemoteFirst;
    } finally {
      syncLock.unlock();
    }
  }

  /**
   * Performs a single synchronization pass in both the local and remote directions; the order
   * of which does not matter. If switching the order produces different results after one pass,
   * then there is a bug.
   *
   * @return whether or not the synchronization pass was successful.
   */
  public boolean doSyncPass() {
    if (!syncLock.tryLock()) {
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
      if (!networkMonitor.isConnected()) {
        logger.info(String.format(
            Locale.US,
            "t='%d': doSyncPass END - Network disconnected",
            logicalT));
        return false;
      }
      if (!authMonitor.isLoggedIn()) {
        logger.info(String.format(
            Locale.US,
            "t='%d': doSyncPass END - Logged out",
            logicalT));
        return false;
      }

      if (localToRemoteFirst) {
        syncLocalToRemote();
        syncRemoteToLocal();
      } else {
        syncRemoteToLocal();
        syncLocalToRemote();
      }

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
   * full document lookup. Any conflicts that occur will be resolved locally and later relayed
   * remotely on a subsequent iteration of {@link DataSynchronizer#doSyncPass()}.
   */
  private void syncRemoteToLocal() {
    logger.info(String.format(
        Locale.US,
        "t='%d': syncRemoteToLocal START",
        logicalT));

    for (final NamespaceSynchronizationConfig nsConfig : syncConfig) {
      final MongoCollection<BsonDocument> localColl = getLocalCollection(nsConfig.getNamespace());
      final Collection<Map.Entry<BsonValue, ChangeEvent<BsonDocument>>> remoteChangeEvents =
          instanceChangeStreamListener.getEventsForNamespace(nsConfig.getNamespace());

      final Set<BsonValue> unseenIds = nsConfig.getSynchronizedDocumentIds();

      for (final Map.Entry<BsonValue, ChangeEvent<BsonDocument>> eventEntry : remoteChangeEvents) {
        final CoreDocumentSynchronizationConfig docConfig =
            nsConfig.getSynchronizedDocument(eventEntry.getKey());
        if (docConfig == null) {
          // Not interested in this event.
          continue;
        }
        unseenIds.remove(docConfig.getDocumentId());
        syncRemoteChangeEventToLocal(nsConfig, docConfig, eventEntry.getValue(), localColl);
      }

      for (final BsonValue docId : unseenIds) {
        final CoreDocumentSynchronizationConfig docConfig =
            nsConfig.getSynchronizedDocument(docId);
        if (docConfig == null) {
          continue;
        }

        // We need to look up the document if:
        // 1. We've never recorded a version and we have no record of the document.
        if (docConfig.getLastKnownRemoteVersion() == null
            && localColl.find(getDocumentIdFilter(docConfig.getDocumentId())).first() == null) {
          logger.info(String.format(
              Locale.US,
              "t='%d': syncRemoteToLocal ns=%s documentId=%s no change stream info; looking up "
                  + "the document",
              logicalT,
              nsConfig.getNamespace(),
              docConfig.getDocumentId()));
          final CoreRemoteMongoCollection<BsonDocument> remoteColl =
              getRemoteCollection(nsConfig.getNamespace());
          final ChangeEvent<BsonDocument> remoteChangeEvent =
              getSynthesizedRemoteChangeEventForDocument(remoteColl, docConfig.getDocumentId());
          syncRemoteChangeEventToLocal(nsConfig, docConfig, remoteChangeEvent, localColl);
        }
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
   * @param nsConfig the namespace configuration.
   * @param docConfig the document configuration related to the event.
   * @param remoteChangeEvent the remote change event to synchronize into the local database.
   * @param localColl the local collection where the document lives.
   */
  private void syncRemoteChangeEventToLocal(
      final NamespaceSynchronizationConfig nsConfig,
      final CoreDocumentSynchronizationConfig docConfig,
      final ChangeEvent<BsonDocument> remoteChangeEvent,
      final MongoCollection<BsonDocument> localColl
  ) {
    if (docConfig.hasPendingWrites() && docConfig.getLastResolution() == logicalT) {
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

    final BsonDocument remoteDoc = remoteChangeEvent.getFullDocument();
    final BsonDocument docFilter = getDocumentIdFilter(docConfig.getDocumentId());
    final BsonDocument localDocument = localColl.find(docFilter).first();

    final BsonValue lastKnownRemoteVersion;
    if (remoteDoc == null) {
      lastKnownRemoteVersion = null;
    } else {
      lastKnownRemoteVersion = DocumentVersionInfo
          .getRemoteVersionInfo(remoteDoc).getCurrentVersion();

      if (docConfig.hasCommittedVersion(lastKnownRemoteVersion)) {
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
    }

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
              DocumentVersionInfo.getDocumentVersion(remoteChangeEvent.getFullDocument()));
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
          logger.error(String.format(
              Locale.US,
              "t='%d': syncRemoteChangeEventToLocal ns=%s documentId=%s unknown operation type "
                  + "occurred on the document: %s; dropping the event",
              logicalT,
              nsConfig.getNamespace(),
              docConfig.getDocumentId(),
              remoteChangeEvent.getOperationType().toString()));
          break;
      }
    }

    // Check for pending writes on this version
    {
      final BsonValue lastKnownLocalVersion;
      if (localDocument == null) {
        lastKnownLocalVersion = docConfig.getLastKnownRemoteVersion();
      } else {
        lastKnownLocalVersion = DocumentVersionInfo
            .getLocalVersionInfo(docConfig, localDocument).getCurrentVersion();
      }

      // There is a pending write that must skip R2L if both versions are null. The absence of a
      // version is effectively a version. The pending write, if it's not a delete, should be
      // setting a new version anyway.
      if (lastKnownLocalVersion == null && lastKnownRemoteVersion == null) {
        logger.info(String.format(
            Locale.US,
            "t='%d': syncRemoteChangeEventToLocal ns=%s documentId=%s remote and local have same "
                + "empty version but a write is pending; waiting for next L2R pass",
            logicalT,
            nsConfig.getNamespace(),
            docConfig.getDocumentId()));
        return;
      }
    }

    resolveConflict(
        nsConfig.getNamespace(),
        docConfig,
        remoteChangeEvent);
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

    // Search for modifications in each namespace.
    for (final NamespaceSynchronizationConfig nsConfig : syncConfig) {
      final CoreRemoteMongoCollection<BsonDocument> remoteColl =
          getRemoteCollection(nsConfig.getNamespace());

      for (final CoreDocumentSynchronizationConfig docConfig : nsConfig) {
        if (!docConfig.hasPendingWrites()) {
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

        switch (localChangeEvent.getOperationType()) {
          case INSERT: {
            // It's possible that we may insert after a delete happened and we didn't get a
            // notification for it. There's nothing we can do about this.
            try {
              remoteColl.insertOne(localChangeEvent.getFullDocument());
            } catch (final StitchServiceException ex) {
              if (ex.getErrorCode() != StitchServiceErrorCode.MONGODB_ERROR
                  || !ex.getMessage().contains("E11000")) {
                logger.error(String.format(
                    Locale.US,
                    "t='%d': syncLocalToRemote ns=%s documentId=%s exception inserting: %s",
                    logicalT,
                    nsConfig.getNamespace(),
                    docConfig.getDocumentId(),
                    ex));
                continue;
              }
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

          case REPLACE: {
            if (localDoc == null) {
              throw new IllegalStateException(
                  "expected document to exist for local replace change event: %s");
            }
            final DocumentVersionInfo versionInfo =
                DocumentVersionInfo.getLocalVersionInfo(docConfig, localDoc);
            localDoc.put(DOCUMENT_VERSION_FIELD, versionInfo.getCurrentVersion());

            final RemoteUpdateResult result;
            try {
              result = remoteColl.updateOne(
                  versionInfo.getFilter(),
                  localDoc);
            } catch (final StitchServiceException ex) {
              logger.error(String.format(
                  Locale.US,
                  "t='%d': syncLocalToRemote ns=%s documentId=%s exception replacing: %s",
                  logicalT,
                  nsConfig.getNamespace(),
                  docConfig.getDocumentId(),
                  ex));
              continue;
            }
            if (result.getMatchedCount() == 0) {
              isConflicted = true;
              logger.info(String.format(
                  Locale.US,
                  "t='%d': syncLocalToRemote ns=%s documentId=%s version different on replaced "
                      + "document or document deleted; raising conflict",
                  logicalT,
                  nsConfig.getNamespace(),
                  docConfig.getDocumentId()));
            }
            break;
          }

          case UPDATE: {
            if (localDoc == null) {
              throw new IllegalStateException(
                  "expected document to exist for local update change event");
            }
            final DocumentVersionInfo versionInfo =
                DocumentVersionInfo.getLocalVersionInfo(docConfig, localDoc);
            localDoc.put(DOCUMENT_VERSION_FIELD, versionInfo.getCurrentVersion());

            final BsonDocument translatedUpdate = new BsonDocument();
            if (!localChangeEvent.getUpdateDescription().getUpdatedFields().isEmpty()) {
              final BsonDocument sets = new BsonDocument();
              for (final Map.Entry<String, BsonValue> fieldValue :
                  localChangeEvent.getUpdateDescription().getUpdatedFields().entrySet()) {
                sets.put(fieldValue.getKey(), fieldValue.getValue());
              }
              sets.put(DOCUMENT_VERSION_FIELD, versionInfo.getCurrentVersion());
              translatedUpdate.put("$set", sets);
            }
            if (!localChangeEvent.getUpdateDescription().getRemovedFields().isEmpty()) {
              final BsonDocument unsets = new BsonDocument();
              for (final String field :
                  localChangeEvent.getUpdateDescription().getRemovedFields()) {
                unsets.put(field, BsonBoolean.TRUE);
              }
              translatedUpdate.put("$unset", unsets);
            }

            final RemoteUpdateResult result;
            try {
              result = remoteColl.updateOne(
                  versionInfo.getFilter(),
                  translatedUpdate.isEmpty()
                      // See: changeEventForLocalUpdate for why we do this
                      ? localDoc : translatedUpdate);
            } catch (final StitchServiceException ex) {
              logger.error(String.format(
                  Locale.US,
                  "t='%d': syncLocalToRemote ns=%s documentId=%s exception updating: %s",
                  logicalT,
                  nsConfig.getNamespace(),
                  docConfig.getDocumentId(),
                  ex));
              continue;
            }
            if (result.getMatchedCount() == 0) {
              isConflicted = true;
              logger.info(String.format(
                  Locale.US,
                  "t='%d': syncLocalToRemote ns=%s documentId=%s version different on updated "
                      + "document or document deleted; raising conflict",
                  logicalT,
                  nsConfig.getNamespace(),
                  docConfig.getDocumentId()));
            }
            break;
          }

          case DELETE: {
            final RemoteDeleteResult result;
            try {
              result = remoteColl.deleteOne(DocumentVersionInfo.getVersionedFilter(
                  docConfig.getDocumentId(),
                  docConfig.getLastKnownRemoteVersion()));
            } catch (final StitchServiceException ex) {
              logger.error(String.format(
                  Locale.US,
                  "t='%d': syncLocalToRemote ns=%s documentId=%s exception deleting: %s",
                  logicalT,
                  nsConfig.getNamespace(),
                  docConfig.getDocumentId(),
                  ex));
              continue;
            }
            if (result.getDeletedCount() == 0) {
              remoteDocument = remoteColl.find(docFilter).first();
              remoteDocumentFetched = true;
              if (remoteDocument != null) {
                isConflicted = true;
                logger.info(String.format(
                    Locale.US,
                    "t='%d': syncLocalToRemote ns=%s documentId=%s version different on removed "
                        + "document; raising conflict",
                    logicalT,
                    nsConfig.getNamespace(),
                    docConfig.getDocumentId()));
              } else {
                desyncDocumentFromRemote(nsConfig.getNamespace(), docConfig.getDocumentId());
              }
            } else {
              desyncDocumentFromRemote(nsConfig.getNamespace(), docConfig.getDocumentId());
            }
            break;
          }

          default:
            logger.error(String.format(
                Locale.US,
                "t='%d': syncLocalToRemote ns=%s documentId=%s unknown operation type occurred "
                    + "on the document: %s; dropping the event",
                logicalT,
                nsConfig.getNamespace(),
                docConfig.getDocumentId(),
                localChangeEvent.getOperationType().toString()));
        }

        logger.info(String.format(
            Locale.US,
            "t='%d': syncLocalToRemote ns=%s documentId=%s conflict=%s",
            logicalT,
            nsConfig.getNamespace(),
            docConfig.getDocumentId(),
            isConflicted));

        if (isConflicted) {
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
        } else {
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
          docConfig.setPendingWritesComplete(DocumentVersionInfo.getDocumentVersion(localDoc));
        }
      }
    }

    logger.info(String.format(
        Locale.US,
        "t='%d': syncLocalToRemote END",
        logicalT));
  }

  /**
   * Resolves a conflict between a synchronized document's local and remote state. The resolution
   * will result in either the document being desynchronized or being replaced with some resolved
   * state based on the conflict resolver specified for the document.
   *
   * @param namespace the namespace where the document lives.
   * @param docConfig the configuration of the document that describes the resolver and current
   *                  state.
   * @param remoteEvent the remote change event that is conflicting.
   */
  private void resolveConflict(
      final MongoNamespace namespace,
      final CoreDocumentSynchronizationConfig docConfig,
      final ChangeEvent<BsonDocument> remoteEvent
  ) {
    if (docConfig.getConflictResolver() == null) {
      logger.warn(String.format(
          Locale.US,
          "t='%d': resolveConflict ns=%s documentId=%s no conflict resolver set; cannot resolve "
              + "yet",
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

    final Object resolvedDocument;
    try {
      final ChangeEvent transformedLocalEvent = ChangeEvent.transformChangeEventForUser(
          docConfig.getLastUncommittedChangeEvent(), docConfig.getDocumentCodec());
      final ChangeEvent transformedRemoteEvent =
          ChangeEvent.transformChangeEventForUser(remoteEvent, docConfig.getDocumentCodec());
      resolvedDocument = resolveConflictWithResolver(
          docConfig.getConflictResolver(),
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
      return;
    }

    final BsonValue remoteVersion;
    if (remoteEvent.getOperationType() == ChangeEvent.OperationType.DELETE) {
      // We expect there will be no version on the document. Note: it's very possible
      // that the document could be reinserted at this point with no version field and we
      // would end up deleting it, unless we receive a notification in time.
      remoteVersion = null;
    } else {
      final DocumentVersionInfo remoteVersionInfo = DocumentVersionInfo
          .getRemoteVersionInfo(remoteEvent.getFullDocument());
      remoteVersion = remoteVersionInfo.getCurrentVersion();
    }

    final boolean acceptRemote =
        (remoteEvent.getFullDocument() == null && resolvedDocument == null)
            || (remoteEvent.getFullDocument() != null
            && remoteEvent.getFullDocument().equals(resolvedDocument));

    if (resolvedDocument == null) {
      logger.info(String.format(
          Locale.US,
          "t='%d': resolveConflict ns=%s documentId=%s deleting local and remote with remote "
              + "version acknowledged",
          logicalT,
          namespace,
          docConfig.getDocumentId()));

      if (acceptRemote) {
        deleteOneFromRemote(namespace, docConfig.getDocumentId());
      } else {
        deleteOneFromResolution(namespace, docConfig.getDocumentId(), remoteVersion);
      }
    } else {
      // Update the document locally which will keep the pending writes but with
      // a new version next time around.
      final BsonDocument docForStorage =
          BsonUtils.documentToBsonDocument(resolvedDocument, docConfig.getDocumentCodec());

      logger.info(String.format(
          Locale.US,
          "t='%d': resolveConflict ns=%s documentId=%s replacing local with resolved document with "
              + "remote version acknowledged: %s",
          logicalT,
          namespace,
          docConfig.getDocumentId(),
          docForStorage.toJson()));

      if (acceptRemote) {
        replaceOrUpsertOneFromRemote(
            namespace,
            docConfig.getDocumentId(),
            docForStorage,
            remoteVersion);
      } else {
        replaceOrUpsertOneFromResolution(
            namespace,
            docConfig.getDocumentId(),
            docForStorage,
            remoteVersion,
            remoteEvent.getOperationType() == ChangeEvent.OperationType.DELETE);
      }
    }
  }

  /**
   * Returns the resolution of resolving the conflict between a local and remote event using
   * the given conflict resolver.
   *
   * @param conflictResolver the conflict resolver to use.
   * @param documentId the document id related to the conflicted events.
   * @param localEvent the conflicted local event.
   * @param remoteEvent the conflicted remote event.
   * @return the resolution to the conflict.
   */
  @SuppressWarnings("unchecked")
  private static Object resolveConflictWithResolver(
      final SyncConflictResolver conflictResolver,
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
   * @param ns the namspace where the document lives.
   * @param documentId the _id of the document.
   * @param document the remote document.
   * @return a synthesized change event for a remote document.
   */
  private ChangeEvent<BsonDocument> getSynthesizedRemoteChangeEventForDocument(
      final MongoNamespace ns,
      final BsonValue documentId,
      final BsonDocument document
  ) {
    if (document == null) {
      return changeEventForLocalDelete(ns, documentId, false);
    }
    return changeEventForLocalInsert(ns, document, false);
  }

  /**
   * Asks the change stream listener to do a sweep.
   */
  public void doListenerSweep() {
    instanceChangeStreamListener.sweep();
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
   * Requests that a document be synchronized by the given _id. Actual synchronization of the
   * document will happen later in a {@link DataSynchronizer#doSyncPass()} iteration.
   *
   * @param namespace the namespace to put the document in.
   * @param documentId the _id of the document.
   * @param conflictResolver the conflict resolver that will handle any conflicts for this document.
   * @param documentCodec the {@link Codec} that can encode/decode documents from the collection.
   * @param <T> the type of the document in the collection.
   */
  public <T> void syncDocumentFromRemote(
      final MongoNamespace namespace,
      final BsonValue documentId,
      final SyncConflictResolver<T> conflictResolver,
      final Codec<T> documentCodec
  ) {
    syncConfig
        .addSynchronizedDocument(namespace, documentId, conflictResolver, documentCodec);
    addAndStartListeningToNamespace(namespace);
  }

  /**
   * Requests that a document be no longer be synchronized by the given _id. Any uncommitted writes
   * will be lost.
   *
   * @param namespace the namespace to put the document in.
   * @param documentId the _id of the document.
   */
  public void desyncDocumentFromRemote(
      final MongoNamespace namespace,
      final BsonValue documentId
  ) {
    syncConfig.removeSynchronizedDocument(namespace, documentId);
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
    // TODO: lock down ids
    final Set<BsonValue> syncedIds = getSynchronizedDocumentIds(namespace);
    final BsonDocument finalFilter = new BsonDocument("$and", new BsonArray(Arrays.asList(
        new BsonDocument("_id", new BsonDocument("$in", new BsonArray(new ArrayList<>(syncedIds)))),
        filter
    )));
    return getLocalCollection(namespace, resultClass, codecRegistry)
        .find(finalFilter)
        .limit(limit)
        .projection(projection)
        .sort(sort)
        .into(new ArrayList<T>());
  }

  /**
   * Finds a single synchronized document by the given _id. If the document is not being
   * synchronized or has not yet been found remotely, null will be returned.
   *
   * @param namespace the namespace to search for the document in.
   * @param documentId the _id of the document.
   * @param resultClass the {@link Class} that represents this document in the collection.
   * @param codecRegistry the {@link CodecRegistry} that contains a codec for resultClass.
   * @param <T> the type of the document in the collection.
   * @return the synchronized document if it exists; null otherwise.
   */
  public <T> T findOneById(
      final MongoNamespace namespace,
      final BsonValue documentId,
      final Class<T> resultClass,
      final CodecRegistry codecRegistry
  ) {
    // TODO: lock down id
    if (!syncConfig.isDocumentSynchronized(namespace, documentId)) {
      return null;
    }

    final BsonDocument filter = new BsonDocument("_id", documentId);
    return getLocalCollection(namespace, resultClass, codecRegistry).find(filter).first();
  }

  /**
   * Inserts a single document locally and being to synchronize it based on its _id. Inserting
   * a document with the same _id twice will result in a duplicate key exception.
   *
   * @param <T> the type of the document in the collection.
   * @param namespace the namespace to put the document in.
   * @param document the document to insert.
   * @param conflictResolver the conflict resolver that will handle any conflicts for this document.
   * @param eventListener the event listener to invoke when a a change event happens for the
   *                      document.
   * @param documentCodec the {@link Codec} that can encode/decode documents from the collection.
   */
  public <T> void insertOneAndSync(
      final MongoNamespace namespace,
      final BsonDocument document,
      final SyncConflictResolver<T> conflictResolver,
      final ChangeEventListener<T> eventListener,
      final Codec<T> documentCodec
  ) {
    final BsonDocument docToInsert = withNewVersionId(document);
    getLocalCollection(namespace).insertOne(docToInsert);
    final ChangeEvent<BsonDocument> event =
        changeEventForLocalInsert(namespace, docToInsert, true);
    syncConfig.addSynchronizedDocument(
        namespace,
        BsonUtils.getDocumentId(docToInsert),
        conflictResolver,
        documentCodec)
        .setSomePendingWrites(logicalT, event);
    addAndStartListeningToNamespace(namespace);
    if (eventListener != null) {
      watchDocument(namespace, BsonUtils.getDocumentId(docToInsert), eventListener, documentCodec);
    }
    emitEvent(BsonUtils.getDocumentId(docToInsert), event);
  }

  /**
   * Updates a single synchronized document by its given id with the given update specifiers.
   * No update will occur if the _id is not being synchronized.
   *
   * @param namespace the namespace where the document lives.
   * @param documentId the _id of the document.
   * @param update the update modifiers.
   * @return the result of the update.
   */
  public UpdateResult updateOneById(
      final MongoNamespace namespace,
      final BsonValue documentId,
      final BsonDocument update
  ) {
    // TODO: lock down id
    final CoreDocumentSynchronizationConfig config =
        syncConfig.getSynchronizedDocument(namespace, documentId);
    if (config == null) {
      return UpdateResult.acknowledged(0, 0L, null);
    }

    final BsonDocument updateWithVersion = withNewVersionIdSet(update);
    final BsonDocument result = getLocalCollection(namespace)
        .findOneAndUpdate(
            getDocumentIdFilter(documentId),
            updateWithVersion,
            new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
    if (result == null) {
      return UpdateResult.acknowledged(0, 0L, null);
    }
    final ChangeEvent<BsonDocument> event =
        changeEventForLocalUpdate(namespace, documentId, updateWithVersion, result, true);
    config.setSomePendingWrites(
        logicalT,
        event);
    emitEvent(documentId, event);
    return UpdateResult.acknowledged(1, 1L, null);
  }

  /**
   * Replaces a single synchronized document by its given id with the given full document
   * replacement. No replacement will occur if the _id is not being synchronized.
   *
   * @param namespace the namespace where the document lives.
   * @param documentId the _id of the document.
   * @param document the replacement document.
   */
  private void replaceOrUpsertOneFromResolution(
      final MongoNamespace namespace,
      final BsonValue documentId,
      final BsonDocument document,
      final BsonValue atVersion,
      final boolean fromDelete
  ) {
    // TODO: lock down id
    final CoreDocumentSynchronizationConfig config =
        syncConfig.getSynchronizedDocument(namespace, documentId);
    if (config == null) {
      return;
    }

    final BsonDocument docToReplace = withNewVersionId(document);
    final BsonDocument result = getLocalCollection(namespace)
        .findOneAndReplace(
            getDocumentIdFilter(documentId),
            docToReplace,
            new FindOneAndReplaceOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
    final ChangeEvent<BsonDocument> event;
    if (fromDelete) {
      event = changeEventForLocalInsert(namespace, result, true);
      config.setSomePendingWrites(
          logicalT,
          atVersion,
          event);
    } else {
      event = changeEventForLocalReplace(namespace, documentId, result, true);
      config.setSomePendingWrites(
          logicalT,
          atVersion,
          event);
    }
    emitEvent(documentId, event);
  }

  /**
   * Replaces a single synchronized document by its given id with the given full document
   * replacement. No replacement will occur if the _id is not being synchronized.
   *
   * @param namespace the namespace where the document lives.
   * @param documentId the _id of the document.
   * @param document the replacement document.
   */
  private void replaceOrUpsertOneFromRemote(
      final MongoNamespace namespace,
      final BsonValue documentId,
      final BsonDocument document,
      final BsonValue atVersion
  ) {
    // TODO: lock down id
    final CoreDocumentSynchronizationConfig config =
        syncConfig.getSynchronizedDocument(namespace, documentId);
    if (config == null) {
      return;
    }

    getLocalCollection(namespace)
        .findOneAndReplace(
            getDocumentIdFilter(documentId),
            document,
            new FindOneAndReplaceOptions().upsert(true));
    config.setPendingWritesComplete(atVersion);
    emitEvent(documentId, changeEventForLocalReplace(namespace, documentId, document, false));
  }

  /**
   * Deletes a single synchronized document by its given id. No deletion will occur if the _id is
   * not being synchronized.
   *
   * @param namespace the namespace where the document lives.
   * @param documentId the _id of the document.
   * @return the result of the deletion.
   */
  public DeleteResult deleteOneById(
      final MongoNamespace namespace,
      final BsonValue documentId
  ) {
    // TODO: lock down id
    final CoreDocumentSynchronizationConfig config =
        syncConfig.getSynchronizedDocument(namespace, documentId);
    if (config == null) {
      return DeleteResult.acknowledged(0);
    }

    final DeleteResult result = getLocalCollection(namespace)
        .deleteOne(getDocumentIdFilter(documentId));
    final ChangeEvent<BsonDocument> event =
        changeEventForLocalDelete(namespace, documentId, true);
    config.setSomePendingWrites(
        logicalT, event);
    emitEvent(documentId, event);
    return result;
  }

  /**
   * Deletes a single synchronized document by its given id. No deletion will occur if the _id is
   * not being synchronized.
   *
   * @param namespace the namespace where the document lives.
   * @param documentId the _id of the document.
   */
  private void deleteOneFromResolution(
      final MongoNamespace namespace,
      final BsonValue documentId,
      final BsonValue atVersion
  ) {
    // TODO: lock down id
    final CoreDocumentSynchronizationConfig config =
        syncConfig.getSynchronizedDocument(namespace, documentId);
    if (config == null) {
      return;
    }

    getLocalCollection(namespace)
        .deleteOne(getDocumentIdFilter(documentId));
    final ChangeEvent<BsonDocument> event =
        changeEventForLocalDelete(namespace, documentId, true);
    config.setSomePendingWrites(
        logicalT, atVersion, event);
    emitEvent(documentId, event);
  }

  /**
   * Deletes a single synchronized document by its given id. No deletion will occur if the _id is
   * not being synchronized.
   *
   * @param namespace the namespace where the document lives.
   * @param documentId the _id of the document.
   */
  private void deleteOneFromRemote(
      final MongoNamespace namespace,
      final BsonValue documentId
  ) {
    // TODO: lock down id
    final CoreDocumentSynchronizationConfig config =
        syncConfig.getSynchronizedDocument(namespace, documentId);
    if (config == null) {
      return;
    }
    getLocalCollection(namespace).deleteOne(getDocumentIdFilter(documentId));
    desyncDocumentFromRemote(namespace, documentId);
    emitEvent(documentId, changeEventForLocalDelete(namespace, documentId, false));
  }

  private void addAndStartListeningToNamespace(final MongoNamespace namespace) {
    instanceChangeStreamListener.addNamespace(namespace);
    syncLock.lock();
    try {
      if (syncThread == null) {
        return;
      }
      instanceChangeStreamListener.start();
    } finally {
      syncLock.unlock();
    }
  }

  /**
   * Requests that the given document be watched for change events.
   *
   * @param namespace the namespace where the document lives.
   * @param documentId the _id of the document.
   * @param eventListener the listener to invoke when an event happens.
   * @param documentCodec the codec that can encode/decode the class of documents.
   * @param <T> the type of the document in the collection.
   */
  // TODO: Allow multiple watchers per doc?
  public <T> void watchDocument(
      final MongoNamespace namespace,
      final BsonValue documentId,
      final ChangeEventListener<T> eventListener,
      final Codec<T> documentCodec
  ) {
    listenersLock.lock();
    try {
      final Map<BsonValue, DocumentListenerConfig> docListeners;
      if (!nsEventListeners.containsKey(namespace)) {
        docListeners = new HashMap<>();
        nsEventListeners.put(namespace, docListeners);
      } else {
        docListeners = nsEventListeners.get(namespace);
      }
      docListeners.put(documentId, new DocumentListenerConfig(eventListener, documentCodec));
    } finally {
      listenersLock.unlock();
    }
  }

  /**
   * Emits a change event for the given document id.
   *
   * @param documentId the document that has a change event for it.
   * @param event the change event.
   */
  private void emitEvent(final BsonValue documentId, final ChangeEvent<BsonDocument> event) {
    listenersLock.lock();
    try {
      if (!nsEventListeners.containsKey(event.getNamespace())) {
        return;
      }
      final Map<BsonValue, DocumentListenerConfig> docListeners =
          nsEventListeners.get(event.getNamespace());
      if (!docListeners.containsKey(documentId)) {
        return;
      }
      final DocumentListenerConfig docListenerConfig = docListeners.get(documentId);
      if (docListenerConfig == null) {
        return;
      }
      eventDispatcher.dispatch(new Callable<Object>() {
        @Override
        @SuppressWarnings("unchecked")
        public Object call() {
          try {
            docListenerConfig.getEventListener().onEvent(
                documentId,
                ChangeEvent.transformChangeEventForUser(
                    event, docListenerConfig.getDocumentCodec()));
          } catch (final Exception ex) {
            logger.error(String.format(
                Locale.US,
                "emitEvent ns=%s documentId=%s emit exception: %s",
                event.getNamespace(),
                documentId,
                ex));
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
   * Returns the local collection representing the given namespace.
   *
   * @param namespace the namespace referring to the local collection.
   * @param resultClass the {@link Class} that represents documents in the collection.
   * @param <T> the type documents in the collection.
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
   * @param namespace the namespace referring to the remote collection.
   * @param resultClass the {@link Class} that represents documents in the collection.
   * @param <T> the type documents in the collection.
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

  /**
   * Returns a query filter searching for the given document _id.
   * @param documentId the _id of the document to search for.
   * @return a query filter searching for the given document _id.
   */
  private static BsonDocument getDocumentIdFilter(final BsonValue documentId) {
    return new BsonDocument("_id", documentId);
  }

  /**
   * Adds and returns a document with a new version id to the given document.
   * @param document the document to attach a new version to.
   * @return a document with a new version id to the given document.
   */
  private static BsonDocument withNewVersionId(final BsonDocument document) {
    final BsonDocument newDocument = BsonUtils.copyOfDocument(document);
    newDocument.put(DOCUMENT_VERSION_FIELD, new BsonString(UUID.randomUUID().toString()));
    return newDocument;
  }

  /**
   * Adds and returns a document with a new version id set modifier to the given document.
   * @param document the document to attach a new version set modifier to.
   * @return a document with a new version id set modifier to the given document.
   */
  private static BsonDocument withNewVersionIdSet(final BsonDocument document) {
    return BsonUtils.mergeSubdocumentAtKey(
        "$set",
        document,
        new BsonDocument(DOCUMENT_VERSION_FIELD, new BsonString(UUID.randomUUID().toString())));
  }
}
