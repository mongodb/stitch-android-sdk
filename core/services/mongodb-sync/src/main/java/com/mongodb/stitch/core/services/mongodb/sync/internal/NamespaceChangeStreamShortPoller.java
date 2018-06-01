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

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.StitchServiceErrorCode;
import com.mongodb.stitch.core.StitchServiceException;
import com.mongodb.stitch.core.internal.common.AuthMonitor;
import com.mongodb.stitch.core.internal.net.NetworkMonitor;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.bson.BsonDocument;
import org.bson.BsonTimestamp;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.diagnostics.Logger;
import org.bson.diagnostics.Loggers;

final class NamespaceChangeStreamShortPoller {

  private final MongoNamespace namespace;
  private final InstanceSynchronizationConfig instanceConfig;
  private final NamespaceSynchronizationConfig nsConfig;
  private final CoreStitchServiceClient service;
  private final NetworkMonitor networkMonitor;
  private final AuthMonitor authMonitor;
  private final Logger logger;
  private final List<Map.Entry<BsonValue, ChangeEvent<BsonDocument>>> events;
  private final ReadWriteLock nsLock;
  private Thread pollThread;

  NamespaceChangeStreamShortPoller(
      final MongoNamespace namespace,
      final InstanceSynchronizationConfig instanceConfig,
      final NamespaceSynchronizationConfig nsConfig,
      final CoreStitchServiceClient service,
      final NetworkMonitor networkMonitor,
      final AuthMonitor authMonitor
  ) {
    this.namespace = namespace;
    this.instanceConfig = instanceConfig;
    this.nsConfig = nsConfig;
    this.service = service;
    this.networkMonitor = networkMonitor;
    this.authMonitor = authMonitor;
    this.events = new ArrayList<>();
    this.nsLock = new ReentrantReadWriteLock();
    this.logger =
        Loggers.getLogger(
            String.format("NamespaceChangeStreamShortPoller-%s", namespace.toString()));
  }

  /**
   * Starts polling in a background thread.
   */
  public void start() {
    nsLock.writeLock().lock();
    try {
      if (pollThread != null) {
        return;
      }
      pollThread =
          new Thread(new NamespaceChangeStreamPollerRunner(
              new WeakReference<>(this), networkMonitor, logger));
      pollThread.start();
    } finally {
      nsLock.writeLock().unlock();
    }
  }

  /**
   * Stops the background poller thread.
   */
  public void stop() {
    nsLock.writeLock().lock();
    try {
      if (pollThread == null) {
        return;
      }
      pollThread.interrupt();
      try {
        pollThread.join();
      } catch (final InterruptedException e) {
        return;
      }
      pollThread = null;
    } finally {
      nsLock.writeLock().unlock();
    }
  }

  /**
   * Does a short poll of the change stream.
   */
  boolean poll() {
    logger.info("poll START");
    if (!networkMonitor.isConnected()) {
      logger.info("poll END - Network disconnected");
      return false;
    }
    if (!authMonitor.isLoggedIn()) {
      logger.info("poll END - Logged out");
      return false;
    }

    final Document args = new Document();
    args.put("database", namespace.getDatabaseName());
    args.put("collection", namespace.getCollectionName());

    final Document csOptions = new Document();
    csOptions.put("fullDocument", "updateLookup");
    if (nsConfig.getLastRemoteResumeToken() != null) {
      csOptions.put("resumeAfter", nsConfig.getLastRemoteResumeToken());
    } else {
      final BsonTimestamp remoteClusterTime;
      if ((instanceConfig.getLastRemoteClusterTime() != null)) {
        remoteClusterTime = instanceConfig.getLastRemoteClusterTime();
      } else {
        remoteClusterTime = service.callFunctionInternal(
            "__stitch_alpha_getClusterTime",
            Collections.singletonList(args),
            BsonTimestamp.class);
        instanceConfig.setLastRemoteClusterTime(remoteClusterTime);
      }
      // TODO(SERVER-33818): Detect if should be using startAtOperationTime or not.
      // Need version info.
      csOptions.put("$_resumeAfterClusterTime", new BsonDocument("ts", remoteClusterTime));
    }
    args.put("pipeline", Arrays.asList(
        new Document("$changeStream", csOptions),

        // TODO: Filter out transaction ids on the server as they are not real updates we care
        // to sync.
        new Document("$match",
            new Document("fullDocument._id__stitch_transaction", new Document("$exists", false)))));

    final List<Map.Entry<BsonValue, ChangeEvent<BsonDocument>>> remoteChangeEvents;
    try {
      remoteChangeEvents =
          service.callFunctionInternal(
              "__stitch_alpha_watch",
              Collections.singletonList(args),
              ChangeEvent.changeEventsDecoder);
    } catch (final StitchServiceException ex) {
      if (ex.getErrorCode() == StitchServiceErrorCode.MONGODB_ERROR) {
        if (ex.getMessage().contains("40615")) {
          // Resume token bad, drop it.
          nsConfig.setLastRemoteResumeToken(null);
        }
      }
      logger.error(String.format(
          Locale.US,
          "getRemoteChangeEventsForNamespace ns=%s exception on listening to change stream: %s",
          nsConfig.getNamespace(),
          ex));
      logger.info("poll END");
      return false;
    }

    if (!remoteChangeEvents.isEmpty()) {
      final ChangeEvent<BsonDocument> lastEvent =
          remoteChangeEvents.get(remoteChangeEvents.size() - 1).getValue();
      nsConfig.setLastRemoteResumeToken(lastEvent.getId());
    } else {
      logger.info("poll END");
      return true;
    }

    if (!nsLock.writeLock().tryLock()) {
      logger.info("poll END");
      return false;
    }
    try {
      events.addAll(remoteChangeEvents);
    } finally {
      nsLock.writeLock().unlock();
    }
    logger.info("poll END");
    return true;
  }

  /**
   * Returns the latest change events.
   *
   * @return the latest change events.
   */
  public List<Map.Entry<BsonValue, ChangeEvent<BsonDocument>>> getEvents() {
    this.nsLock.readLock().lock();
    try {
      final List<Map.Entry<BsonValue, ChangeEvent<BsonDocument>>> currentEvents =
          new ArrayList<>(events);
      events.clear();
      return currentEvents;
    } finally {
      this.nsLock.readLock().unlock();
    }
  }
}
