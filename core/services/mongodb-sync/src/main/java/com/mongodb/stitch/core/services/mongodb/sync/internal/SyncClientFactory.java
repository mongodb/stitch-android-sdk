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

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.stitch.core.StitchAppClientInfo;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoClient;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoClientImpl;
import java.util.HashMap;
import java.util.Map;

public abstract class SyncClientFactory {

  private static final Map<String, CoreSyncMongoClient> syncInstances = new HashMap<>();

  public static synchronized CoreSyncMongoClient getClient(
      final CoreStitchServiceClient service,
      final StitchAppClientInfo appInfo
  ) {
    final String instanceKey = String.format("%s-%s", appInfo.getClientAppId(), service.getName());
    if (syncInstances.containsKey(instanceKey)) {
      return syncInstances.get(instanceKey);
    }

    final MongoClient localClient = LocalClientFactory.getClient(appInfo, service.getName());
    final CoreRemoteMongoClient remoteClient = new CoreRemoteMongoClientImpl(service);
    final DataSynchronizer dataSynchronizer =
        new DataSynchronizer(
            instanceKey,
            service,
            localClient,
            remoteClient,
            appInfo.getNetworkMonitor(),
            appInfo.getAuthMonitor());

    final MongoDatabase tempDb = localClient.getDatabase("sync_temp");
    final CoreSyncMongoClient synclient =
        new CoreSyncMongoClientImpl(
            remoteClient,
            dataSynchronizer,
            appInfo.getNetworkMonitor(),
            service,
            tempDb);
    syncInstances.put(instanceKey, synclient);
    dataSynchronizer.start();
    return synclient;
  }
}
