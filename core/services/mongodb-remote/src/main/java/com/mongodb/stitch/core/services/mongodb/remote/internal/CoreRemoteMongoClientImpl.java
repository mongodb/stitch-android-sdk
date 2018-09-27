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

package com.mongodb.stitch.core.services.mongodb.remote.internal;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.stitch.core.internal.common.AuthMonitor;
import com.mongodb.stitch.core.internal.net.NetworkMonitor;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.DataSynchronizer;

public class CoreRemoteMongoClientImpl implements CoreRemoteMongoClient {

  private final CoreStitchServiceClient service;
  private final DataSynchronizer dataSynchronizer;
  private final NetworkMonitor networkMonitor;
  private final MongoDatabase tempDb;

  public CoreRemoteMongoClientImpl(final CoreStitchServiceClient service,
                                   final String instanceKey,
                                   final MongoClient localClient,
                                   final NetworkMonitor networkMonitor,
                                   final AuthMonitor authMonitor) {
    this.service = service;
    this.networkMonitor = networkMonitor;
    this.tempDb = localClient.getDatabase("sync_temp");

    this.dataSynchronizer = new DataSynchronizer(
        instanceKey,
        service,
        localClient,
        this,
        networkMonitor,
        authMonitor
    );
    this.dataSynchronizer.start();
  }

  /**
   * Gets a {@link CoreRemoteMongoDatabaseImpl} instance for the given database name.
   *
   * @param databaseName the name of the database to retrieve
   * @return a {@code CoreRemoteMongoDatabaseImpl} representing the specified database
   */
  public CoreRemoteMongoDatabaseImpl getDatabase(final String databaseName) {
    return new CoreRemoteMongoDatabaseImpl(
      databaseName,
      service,
      dataSynchronizer,
      networkMonitor,
      tempDb
    );
  }

  public DataSynchronizer getDataSynchronizer() {
    return dataSynchronizer;
  }

  @Override
  public void close() {
    this.dataSynchronizer.stop();
    this.dataSynchronizer.close();
  }
}
