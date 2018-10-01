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
import com.mongodb.stitch.core.internal.net.NetworkMonitor;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.bson.BsonDocument;
import org.bson.BsonValue;

final class InstanceChangeStreamListenerImpl implements InstanceChangeStreamListener {

  private final Map<MongoNamespace, NamespaceChangeStreamListener> nsStreamers;
  private final ReadWriteLock instanceLock;
  private final InstanceSynchronizationConfig instanceConfig;
  private final CoreStitchServiceClient service;
  private final NetworkMonitor networkMonitor;
  private final AuthMonitor authMonitor;
  private final StaleDocumentFetcher staleDocumentFetcher;

  InstanceChangeStreamListenerImpl(
      final InstanceSynchronizationConfig instanceConfig,
      final CoreStitchServiceClient service,
      final NetworkMonitor networkMonitor,
      final AuthMonitor authMonitor,
      final StaleDocumentFetcher staleDocumentFetcher
  ) {
    this.instanceConfig = instanceConfig;
    this.service = service;
    this.networkMonitor = networkMonitor;
    this.authMonitor = authMonitor;
    this.staleDocumentFetcher = staleDocumentFetcher;

    this.nsStreamers = new HashMap<>();
    this.instanceLock = new ReentrantReadWriteLock();
  }

  /**
   * Starts all streams.
   */
  public void start() {
    instanceLock.writeLock().lock();
    try {
      for (final Map.Entry<MongoNamespace, NamespaceChangeStreamListener> streamerEntry :
          nsStreamers.entrySet()) {
        this.instanceConfig.getNamespaceConfig(streamerEntry.getKey()).setStaleDocumentIds(
            staleDocumentFetcher.getStaleDocumentIds(
                this.instanceConfig.getNamespaceConfig(streamerEntry.getKey())
            )
        );
        streamerEntry.getValue().start();
      }
    } finally {
      instanceLock.writeLock().unlock();
    }
  }

  /**
   * Stops all streams.
   */
  public void stop() {
    instanceLock.writeLock().lock();
    try {
      for (final NamespaceChangeStreamListener streamer : nsStreamers.values()) {
        streamer.stop();
      }
    } finally {
      instanceLock.writeLock().unlock();
    }
  }

  @Override
  public void queueDisposableWatcher(final Callback<ChangeEvent<BsonDocument>, Object> watcher) {
    for (final NamespaceChangeStreamListener streamers : nsStreamers.values()) {
      streamers.queueWatcher(watcher);
    }
  }

  /**
   * Requests that the given namespace be started listening to for change events.
   *
   * @param namespace the namespace to listen for change events on.
   */
  public void addNamespace(final MongoNamespace namespace) {
    this.instanceLock.writeLock().lock();
    try {
      if (this.nsStreamers.containsKey(namespace)) {
        return;
      }
      final NamespaceChangeStreamListener streamer =
          new NamespaceChangeStreamListener(
              namespace,
              instanceConfig.getNamespaceConfig(namespace),
              service,
              networkMonitor,
              authMonitor);
      this.nsStreamers.put(namespace, streamer);
    } finally {
      this.instanceLock.writeLock().unlock();
    }
  }

  /**
   * Requests that the given namespace stopped being listened to for change events.
   *
   * @param namespace the namespace to stop listening for change events on.
   */
  @Override
  public void removeNamespace(final MongoNamespace namespace) {
    this.instanceLock.writeLock().lock();
    try {
      if (!this.nsStreamers.containsKey(namespace)) {
        return;
      }
      final NamespaceChangeStreamListener streamer = this.nsStreamers.get(namespace);
      streamer.stop();
      this.nsStreamers.remove(namespace);
    } finally {
      this.instanceLock.writeLock().unlock();
    }
  }

  /**
   * Returns the latest change events for a given namespace.
   *
   * @param namespace the namespace to get events for.
   * @return the latest change events for a given namespace.
   */
  public Map<BsonValue, ChangeEvent<BsonDocument>> getEventsForNamespace(
      final MongoNamespace namespace
  ) {
    this.instanceLock.readLock().lock();
    final NamespaceChangeStreamListener streamer;
    try {
      streamer = nsStreamers.get(namespace);
    } finally {
      this.instanceLock.readLock().unlock();
    }
    if (streamer == null) {
      return new HashMap<>();
    }
    return streamer.getEvents();
  }
}
