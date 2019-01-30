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

import com.mongodb.stitch.core.StitchAppClientInfo;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.internal.StitchServiceBinder;
import com.mongodb.stitch.core.services.mongodb.local.internal.EmbeddedMongoClientFactory;
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.DataSynchronizer;
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.SyncMongoClientFactory;

public class CoreRemoteMongoClientImpl implements CoreRemoteMongoClient, StitchServiceBinder {

  private final CoreStitchServiceClient service;
  private final StitchAppClientInfo appInfo;
  private final EmbeddedMongoClientFactory clientFactory;

  private DataSynchronizer dataSynchronizer;

  public CoreRemoteMongoClientImpl(final CoreStitchServiceClient service,
                                   final String instanceKey,
                                   final StitchAppClientInfo appInfo,
                                   final EmbeddedMongoClientFactory clientFactory) {
    this.service = service;
    this.appInfo = appInfo;
    this.clientFactory = clientFactory;
    this.service.bind(this);

    this.dataSynchronizer = new DataSynchronizer(
        instanceKey,
        service,
        SyncMongoClientFactory.getClient(
            appInfo,
            service.getName(),
            clientFactory
        ),
        this,
        appInfo.getNetworkMonitor(),
        appInfo.getAuthMonitor(),
        appInfo.getEventDispatcher()
    );
  }

  @Override
  public void onRebindEvent() {
    this.dataSynchronizer.reinitialize(
        SyncMongoClientFactory.getClient(
            appInfo,
            service.getName(),
            clientFactory
        )
    );
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
      appInfo.getNetworkMonitor()
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
