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
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoClient;

import java.io.Closeable;
import java.util.Set;

// TODO: Unify exceptions from Remote and Local
public interface CoreSyncMongoClient extends CoreRemoteMongoClient, Closeable {

  /**
   * Gets a {@link CoreSyncMongoDatabase} instance for the given database name.
   *
   * @param databaseName the name of the database to retrieve
   * @return a {@code CoreSyncMongoDatabaseImpl} representing the specified database
   */
  CoreSyncMongoDatabase getDatabase(final String databaseName);

  DataSynchronizer getDataSynchronizer();

  /**
   * Returns the set of synchronized namespaces.
   *
   * @return the set of synchronized namespaces.
   */
  Set<MongoNamespace> getSynchronizedNamespaces();

  /**
   * Returns the set of synchronized databases.
   *
   * @return the set of synchronized databases.
   */
  Set<String> getSynchronizedDatabases();
}
