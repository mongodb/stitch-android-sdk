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

import com.mongodb.stitch.core.StitchAppClientInfo;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.local.internal.EmbeddedMongoClientFactory;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoClient;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoClientImpl;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public abstract class CoreRemoteClientFactory {

  private static final Map<String, CoreRemoteMongoClient> syncInstances = new HashMap<>();

  public static synchronized CoreRemoteMongoClient getClient(
      final CoreStitchServiceClient service,
      final StitchAppClientInfo appInfo,
      final EmbeddedMongoClientFactory clientFactory
  ) {
    final String instanceKey = String.format("%s-%s", appInfo.getClientAppId(), service.getName());
    if (syncInstances.containsKey(instanceKey)) {
      return syncInstances.get(instanceKey);
    }

    final CoreRemoteMongoClient syncClient =
        new CoreRemoteMongoClientImpl(
            service,
            instanceKey,
            appInfo,
            clientFactory);
    syncInstances.put(instanceKey, syncClient);
    return syncClient;
  }

  public static synchronized void close() {
    for (final CoreRemoteMongoClient instance : syncInstances.values()) {
      try {
        instance.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    syncInstances.clear();
  }
}
