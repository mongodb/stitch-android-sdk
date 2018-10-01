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

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.internal.common.AuthMonitor;
import com.mongodb.stitch.core.internal.common.Callback;
import com.mongodb.stitch.core.internal.common.OperationResult;
import com.mongodb.stitch.core.internal.common.Stream;
import com.mongodb.stitch.core.internal.net.Event;
import com.mongodb.stitch.core.internal.net.EventType;
import com.mongodb.stitch.core.internal.net.NetworkMonitor;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.diagnostics.Logger;
import org.bson.diagnostics.Loggers;

public class NamespaceChangeStreamListener implements NetworkMonitor.StateListener {
  private final MongoNamespace namespace;
  private final NamespaceSynchronizationConfig nsConfig;
  private final CoreStitchServiceClient service;
  private final NetworkMonitor networkMonitor;
  private final AuthMonitor authMonitor;
  private final Logger logger;
  private final Map<BsonValue, ChangeEvent<BsonDocument>> events;
  private Thread streamThread;
  private ReadWriteLock nsLock;
  private final Deque<Callback<ChangeEvent<BsonDocument>, Object>> callbackQueue;
  private Stream<ChangeEvent<BsonDocument>> currentStream;

  NamespaceChangeStreamListener(
      final MongoNamespace namespace,
      final NamespaceSynchronizationConfig nsConfig,
      final CoreStitchServiceClient service,
      final NetworkMonitor networkMonitor,
      final AuthMonitor authMonitor
  ) {
    this.namespace = namespace;
    this.nsConfig = nsConfig;
    this.service = service;
    this.networkMonitor = networkMonitor;
    this.authMonitor = authMonitor;
    this.events = new HashMap<>();
    this.nsLock = new ReentrantReadWriteLock();
    this.logger =
        Loggers.getLogger(
            String.format("NamespaceChangeStreamListener-%s", namespace.toString()));
    this.callbackQueue = new ArrayDeque<>();
    this.networkMonitor.addNetworkStateListener(this);
  }

  @Override
  public void onNetworkStateChanged() {
    if (!this.networkMonitor.isConnected()) {
      this.stop();
      this.nsConfig.setStale();
    } else {
      this.start();
    }
  }

  /**
   * Opens the stream in a background thread.
   */
  public void start() {
    nsLock.writeLock().lock();
    try {
      if (streamThread != null) {
        return;
      }
      streamThread =
          new Thread(new NamespaceChangeStreamRunner(
              new WeakReference<>(this), networkMonitor, logger));
      streamThread.start();
    } finally {
      nsLock.writeLock().unlock();
    }
  }

  /**
   * Stops the background stream thread.
   */
  public void stop() {
    nsLock.writeLock().lock();
    try {
      if (streamThread == null) {
        return;
      }

      streamThread.interrupt();
      try {
        if (streamThread.isAlive()) {
          close();
          streamThread.join();
        }
      } catch (final Exception e) {
        return;
      }
      streamThread = null;
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      nsLock.writeLock().unlock();
    }
  }

  void queueWatcher(final Callback<ChangeEvent<BsonDocument>, Object> callback) {
    callbackQueue.add(callback);
  }

  void clearWatchers() {
    for (int i = 0; i < callbackQueue.size(); i++) {
      callbackQueue.remove().onComplete(
          OperationResult.<ChangeEvent<BsonDocument>, Object>failedResultOf(null));
    }
  }

  void close() {
    if (currentStream != null) {
      try {
        currentStream.close();
      } catch (final IOException e) {
        e.printStackTrace();
      } finally {
        currentStream = null;
      }
    }
  }

  /**
   * Opens the next next.
   */
  void stream() {
    logger.info("stream START");
    if (!networkMonitor.isConnected()) {
      logger.info("stream END - Network disconnected");
      return;
    }
    if (!authMonitor.isLoggedIn()) {
      logger.info("stream END - Logged out");
      return;
    }

    if (nsConfig.getStaleDocumentIds().isEmpty()) {
      logger.info("stream END - No stale documents");
      return;
    }

    final Document args = new Document();
    args.put("database", namespace.getDatabaseName());
    args.put("collection", namespace.getCollectionName());
    args.put("ids", nsConfig.getStaleDocumentIds());

    currentStream =
        service.streamFunction(
            "watch",
            Collections.singletonList(args),
            ChangeEvent.changeEventCoder);
    try {
      while (currentStream.isOpen()) {
        final Event<ChangeEvent<BsonDocument>> event = currentStream.nextEvent();
        if (event.getEventType() == EventType.ERROR) {
          throw event.getError();
        }

        if (event.getEventType() == EventType.MESSAGE) {
          nsLock.writeLock().lock();
          events.put(event.getData().getDocumentKey(), event.getData());
          nsLock.writeLock().unlock();
        }

        for (int i = 0; i < callbackQueue.size(); i++) {
          callbackQueue.remove().onComplete(OperationResult.successfulResultOf(event.getData()));
        }
      }
    } catch (final Exception ex) {
      ex.printStackTrace();
      logger.error(String.format(
          Locale.US,
          "NamespaceChangeStreamListener::stream ns=%s exception on listening to change next: %s",
          nsConfig.getNamespace(),
          ex));
      logger.info("stream END");
    } finally {
      try {
        close();
        clearWatchers();
      } finally {
        logger.info("stream END");
      }
    }
  }

  /**
   * Returns the latest change events.
   *
   * @return the latest change events.
   */
  @SuppressWarnings("unchecked")
  public Map<BsonValue, ChangeEvent<BsonDocument>> getEvents() {
    nsLock.readLock().lock();
    final Map<BsonValue, ChangeEvent<BsonDocument>> events;
    try {
      events = new HashMap<>(this.events);
    } finally {
      nsLock.readLock().unlock();
    }

    nsLock.writeLock().lock();
    try {
      this.events.clear();
      return events;
    } finally {
      nsLock.writeLock().unlock();
    }
  }
}
