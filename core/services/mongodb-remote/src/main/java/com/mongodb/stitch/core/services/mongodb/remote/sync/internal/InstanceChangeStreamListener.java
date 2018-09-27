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
import com.mongodb.stitch.core.internal.common.Callback;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.BsonDocument;
import org.bson.BsonValue;

interface InstanceChangeStreamListener {

  /**
   * Starts listening.
   */
  void start();

  /**
   * Stops listening.
   */
  void stop();

  /**
   * Queue a one-off watcher for the next event pass.
   */
  void queueDisposableWatcher(Callback<ChangeEvent<BsonDocument>, Object> watcher);

  /**
   * Requests that the given namespace be started listening to for change events.
   *
   * @param namespace the namespace to listen for change events on.
   */
  void addNamespace(final MongoNamespace namespace);

  /**
   * Requests that the given namespace stopped being listened to for change events.
   *
   * @param namespace the namespace to stop listening for change events on.
   */
  void removeNamespace(final MongoNamespace namespace);

  /**
   * Returns the latest change events for a given namespace.
   *
   * @param namespace the namespace to get events for.
   * @return the latest change events for a given namespace.
   */
  Map<BsonValue, ChangeEvent<BsonDocument>> getEventsForNamespace(
      final MongoNamespace namespace);
}