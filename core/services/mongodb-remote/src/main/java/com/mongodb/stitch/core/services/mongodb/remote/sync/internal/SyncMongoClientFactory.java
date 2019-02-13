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
import com.mongodb.stitch.core.services.mongodb.local.internal.EmbeddedMongoClientFactory;

import java.io.File;

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

    final String userId = appInfo.getAuthMonitor().tryIsLoggedIn()
        ? appInfo.getAuthMonitor().getActiveUserId() : "unbound";
    final String instanceKey = String.format(
        "%s-%s_sync_%s_%s", appInfo.getClientAppId(), dataDir, serviceName, userId);
    final String dbPath = String.format(
        "%s/%s/sync_mongodb_%s/%s/0/", dataDir, appInfo.getClientAppId(), serviceName, userId);
    return clientFactory.getClient(instanceKey, dbPath, appInfo.getCodecRegistry());
  }

  /**
   * Delete a database for a given path and userId.
   * @param appInfo the info for this application
   * @param serviceName the name of the associated service
   * @param clientFactory the associated factory that creates clients
   * @param userId the id of the user's to delete
   * @return true if successfully deleted, false if not
   */
  public static boolean deleteDatabase(final StitchAppClientInfo appInfo,
                                       final String serviceName,
                                       final EmbeddedMongoClientFactory clientFactory,
                                       final String userId) {
    final String dataDir = appInfo.getDataDirectory();
    if (dataDir == null) {
      throw new IllegalArgumentException("StitchAppClient not configured with a data directory");
    }

    final String instanceKey = String.format(
        "%s-%s_sync_%s_%s", appInfo.getClientAppId(), dataDir, serviceName, userId);
    final String dbPath = String.format(
        "%s/%s/sync_mongodb_%s/%s/0/", dataDir, appInfo.getClientAppId(), serviceName, userId);
    final MongoClient client =
        clientFactory.getClient(instanceKey, dbPath, appInfo.getCodecRegistry());

    for (final String listDatabaseName : client.listDatabaseNames()) {
      try {
        client.getDatabase(listDatabaseName).drop();
      } catch (Exception e) {
        // do nothing
      }
    }

    client.close();
    clientFactory.removeClient(instanceKey);

    return new File(dbPath).delete();
  }
}
