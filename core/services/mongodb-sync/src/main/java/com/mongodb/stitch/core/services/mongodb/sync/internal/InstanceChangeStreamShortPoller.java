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
import com.mongodb.stitch.core.internal.common.AuthMonitor;
import com.mongodb.stitch.core.internal.net.NetworkMonitor;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.bson.BsonDocument;
import org.bson.BsonValue;

final class InstanceChangeStreamShortPoller implements InstanceChangeStreamListener {

  private final Map<MongoNamespace, NamespaceChangeStreamShortPoller> nsPollers;
  private final ReadWriteLock instanceLock;
  private final InstanceSynchronizationConfig instanceConfig;
  private final CoreStitchServiceClient service;
  private final NetworkMonitor networkMonitor;
  private final AuthMonitor authMonitor;

  InstanceChangeStreamShortPoller(
      final InstanceSynchronizationConfig instanceConfig,
      final CoreStitchServiceClient service,
      final NetworkMonitor networkMonitor,
      final AuthMonitor authMonitor
  ) {
    this.instanceConfig = instanceConfig;
    this.service = service;
    this.networkMonitor = networkMonitor;
    this.authMonitor = authMonitor;
    this.nsPollers = new HashMap<>();
    this.instanceLock = new ReentrantReadWriteLock();
  }

  /**
   * Starts all pollers.
   */
  public void start() {
    instanceLock.writeLock().lock();
    try {
      for (final NamespaceChangeStreamShortPoller poller : nsPollers.values()) {
        poller.start();
      }
    } finally {
      instanceLock.writeLock().unlock();
    }
  }

  /**
   * Stops all pollers.
   */
  public void stop() {
    instanceLock.writeLock().lock();
    try {
      for (final NamespaceChangeStreamShortPoller poller : nsPollers.values()) {
        poller.stop();
      }
    } finally {
      instanceLock.writeLock().unlock();
    }
  }

  /**
   * Asks all pollers to poll.
   */
  public void sweep() {
    instanceLock.writeLock().lock();
    try {
      for (final NamespaceChangeStreamShortPoller poller : nsPollers.values()) {
        poller.poll();
      }
    } finally {
      instanceLock.writeLock().unlock();
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
      if (this.nsPollers.containsKey(namespace)) {
        return;
      }
      final NamespaceChangeStreamShortPoller poller =
          new NamespaceChangeStreamShortPoller(
              namespace,
              instanceConfig,
              instanceConfig.getNamespaceConfig(namespace),
              service,
              networkMonitor,
              authMonitor);
      this.nsPollers.put(namespace, poller);
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
      if (!this.nsPollers.containsKey(namespace)) {
        return;
      }
      final NamespaceChangeStreamShortPoller poller = this.nsPollers.get(namespace);
      poller.stop();
      this.nsPollers.remove(namespace);
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
  public List<Map.Entry<BsonValue, ChangeEvent<BsonDocument>>> getEventsForNamespace(
      final MongoNamespace namespace
  ) {
    this.instanceLock.readLock().lock();
    final NamespaceChangeStreamShortPoller poller;
    try {
      poller = nsPollers.get(namespace);
    } finally {
      this.instanceLock.readLock().unlock();
    }
    if (poller == null) {
      return Collections.emptyList();
    }
    return poller.getEvents();
  }

}
