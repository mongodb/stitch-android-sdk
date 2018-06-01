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

package com.mongodb.stitch.server.services.mongodb.sync.internal;

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.services.mongodb.sync.internal.CoreSyncMongoClient;
import com.mongodb.stitch.core.services.mongodb.sync.internal.DataSynchronizer;
import com.mongodb.stitch.server.services.mongodb.sync.SyncMongoClient;
import com.mongodb.stitch.server.services.mongodb.sync.SyncMongoDatabase;
import java.io.IOException;
import java.util.Set;

public final class SyncMongoClientImpl implements SyncMongoClient {

  private final CoreSyncMongoClient proxy;

  public SyncMongoClientImpl(final CoreSyncMongoClient client) {
    this.proxy = client;
  }

  /**
   * Gets a {@link SyncMongoDatabase} instance for the given database name.
   *
   * @param databaseName the name of the database to retrieve
   * @return a {@code SyncMongoDatabase} representing the specified database
   */
  public SyncMongoDatabase getDatabase(final String databaseName) {
    return new SyncMongoDatabaseImpl(proxy.getDatabase(databaseName));
  }

  /**
   * Returns the set of synchronized namespaces.
   *
   * @return the set of synchronized namespaces.
   */
  @Override
  public Set<MongoNamespace> getSynchronizedNamespaces() {
    return proxy.getSynchronizedNamespaces();
  }

  /**
   * Returns the set of synchronized databases.
   *
   * @return the set of synchronized databases.
   */
  @Override
  public Set<String> getSynchronizedDatabases() {
    return proxy.getSynchronizedDatabases();
  }

  public DataSynchronizer getDataSynchronizer() {
    return proxy.getDataSynchronizer();
  }

  @Override
  public void close() throws IOException {
    proxy.close();
  }
}
