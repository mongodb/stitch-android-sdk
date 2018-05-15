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

package com.mongodb.stitch.core.services.mongodb.local;

import com.mongodb.client.MongoClient;
import com.mongodb.embedded.client.MongoClientSettings;
import com.mongodb.embedded.client.MongoClients;
import com.mongodb.embedded.client.MongoEmbeddedSettings;
import com.mongodb.stitch.core.StitchAppClientInfo;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public abstract class CoreLocalMongoDbService {

  private static final Map<String, MongoClient> localInstances = new HashMap<>();
  private static boolean initialized = false;

  protected static synchronized MongoClient getClient(final StitchAppClientInfo appInfo) {
    if (!initialized) {
      MongoClients.init(MongoEmbeddedSettings.builder().build());
      initialized = true;
    }

    final String dataDir = appInfo.getDataDirectory();
    if (dataDir == null) {
      throw new IllegalArgumentException("StitchAppClient not configured with a data directory");
    }
    final String instanceKey = String.format("%s-%s", appInfo.getClientAppId(), dataDir);
    if (localInstances.containsKey(instanceKey)) {
      return localInstances.get(instanceKey);
    }

    final String dbPath = String.format(
        "%s/%s/local_mongodb/0/", dataDir, appInfo.getClientAppId());
    final MongoClient client =
        MongoClients.create(MongoClientSettings.builder().dbPath(dbPath).build());

    localInstances.put(instanceKey, client);
    return client;
  }

  protected static synchronized Collection<MongoClient> getLocalInstances() {
    return localInstances.values();
  }
}
