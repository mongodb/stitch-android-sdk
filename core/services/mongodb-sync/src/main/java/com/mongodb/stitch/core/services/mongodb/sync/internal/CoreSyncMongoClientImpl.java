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
import com.mongodb.client.MongoDatabase;
import com.mongodb.stitch.core.internal.net.NetworkMonitor;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoClient;

import java.util.HashSet;
import java.util.Set;

public class CoreSyncMongoClientImpl implements CoreSyncMongoClient {

  private final CoreRemoteMongoClient proxy;
  private final DataSynchronizer dataSynchronizer;
  private final NetworkMonitor networkMonitor;
  private final CoreStitchServiceClient service;
  private final MongoDatabase tempDb;

  CoreSyncMongoClientImpl(
      final CoreRemoteMongoClient proxy,
      final DataSynchronizer dataSynchronizer,
      final NetworkMonitor networkMonitor,
      final CoreStitchServiceClient service,
      final MongoDatabase tempDb
  ) {
    this.proxy = proxy;
    this.dataSynchronizer = dataSynchronizer;
    this.networkMonitor = networkMonitor;
    this.service = service;
    this.tempDb = tempDb;
  }

  /**
   * Gets a {@link CoreSyncMongoDatabaseImpl} instance for the given database name.
   *
   * @param databaseName the name of the database to retrieve
   * @return a {@code CoreSyncMongoDatabaseImpl} representing the specified database
   */
  public CoreSyncMongoDatabaseImpl getDatabase(final String databaseName) {
    return new CoreSyncMongoDatabaseImpl(
        proxy.getDatabase(databaseName),
        dataSynchronizer,
        networkMonitor,
        service,
        tempDb);
  }

  public DataSynchronizer getDataSynchronizer() {
    return dataSynchronizer;
  }

  /**
   * Returns the set of synchronized namespaces.
   *
   * @return the set of synchronized namespaces.
   */
  @Override
  public Set<MongoNamespace> getSynchronizedNamespaces() {
    return this.dataSynchronizer.getSynchronizedNamespaces();
  }

  /**
   * Returns the set of synchronized databases.
   *
   * @return the set of synchronized databases.
   */
  @Override
  public Set<String> getSynchronizedDatabases() {
    final Set<String> dbs = new HashSet<>();
    for (final MongoNamespace ns : this.dataSynchronizer.getSynchronizedNamespaces()) {
      dbs.add(ns.getDatabaseName());
    }
    return dbs;
  }

  @Override
  public void close() {
    this.dataSynchronizer.stop();
    this.dataSynchronizer.close();
  }
}
