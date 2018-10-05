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

package com.mongodb.stitch.core.services.mongodb.local.internal;

import com.mongodb.client.MongoClient;
import com.mongodb.stitch.core.StitchAppClientInfo;

public abstract class LocalMongoClientFactory {
  public static MongoClient getClient(
      final StitchAppClientInfo appInfo,
      final EmbeddedMongoClientFactory clientFactory
  ) {
    final String dataDir = appInfo.getDataDirectory();
    if (dataDir == null) {
      throw new IllegalArgumentException("StitchAppClient not configured with a data directory");
    }
    final String instanceKey = String.format("local-%s-%s", appInfo.getClientAppId(), dataDir);
    final String dbPath = String.format(
        "%s/%s/local_mongodb/0/", dataDir, appInfo.getClientAppId());
    return clientFactory.getClient(instanceKey, dbPath, appInfo.getCodecRegistry());
  }
}
