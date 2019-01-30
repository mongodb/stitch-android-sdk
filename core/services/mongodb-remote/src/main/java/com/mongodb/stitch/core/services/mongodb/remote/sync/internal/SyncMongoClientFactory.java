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

import com.mongodb.client.MongoClient;
import com.mongodb.stitch.core.StitchAppClientInfo;
import com.mongodb.stitch.core.internal.common.AuthMonitor;
import com.mongodb.stitch.core.services.mongodb.local.internal.EmbeddedMongoClientFactory;

public abstract class SyncMongoClientFactory {
  public static MongoClient getClient(
      final StitchAppClientInfo appInfo,
      final String serviceName,
      final EmbeddedMongoClientFactory clientFactory
  ) {
    final String dataDir = appInfo.getDataDirectory();
    if (dataDir == null) {
      throw new IllegalArgumentException("StitchAppClient not configured with a data directory");
    }

    final String userId = appInfo.getAuthMonitor().isLoggedIn() ?
        appInfo.getAuthMonitor().getActiveUserId() : "unbound";
    final String instanceKey = String.format(
        "%s-%s_sync_%s_%s", appInfo.getClientAppId(), dataDir, serviceName, userId);
    final String dbPath = String.format(
        "%s/%s/sync_mongodb_%s/%s/0/", dataDir, appInfo.getClientAppId(), serviceName, userId);
    return clientFactory.getClient(instanceKey, dbPath, appInfo.getCodecRegistry());
  }
}
