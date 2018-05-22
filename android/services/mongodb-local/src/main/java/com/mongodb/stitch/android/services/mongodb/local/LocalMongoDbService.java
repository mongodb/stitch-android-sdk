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

package com.mongodb.stitch.android.services.mongodb.local;

import android.util.Log;

import com.mongodb.client.MongoClient;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.android.core.services.internal.ServiceClientFactory;
import com.mongodb.stitch.android.core.services.internal.StitchService;
import com.mongodb.stitch.android.services.mongodb.local.internal.MongoDbMobileProvider;
import com.mongodb.stitch.core.StitchAppClientInfo;
import com.mongodb.stitch.core.services.mongodb.local.internal.CoreLocalMongoDbService;

import org.bson.Document;

/**
 * The LocalMongoDbService is used to access {@link LocalMongoDbService#ClientFactory} which
 * provides MongoClients used for local/offline storage using the embedded MongoDB platform.
 */
public final class LocalMongoDbService extends CoreLocalMongoDbService {

  private static final String TAG = LocalMongoDbService.class.getSimpleName();

  private static final String ADMIN_DATABASE_NAME = "admin";
  public static final ServiceClientFactory<MongoClient> ClientFactory =
      new ServiceClientFactory<MongoClient>() {

        @Override
        public synchronized MongoClient getClient(
            final StitchService stitchService,
            final StitchAppClientInfo appInfo,
            final TaskDispatcher dispatcher
        ) {

          return CoreLocalMongoDbService.getClient(appInfo);
        }
      };

  static  {
    MongoDbMobileProvider.addEventListener(
        new MongoDbMobileProvider.EventListener() {
          @Override
          public void onLowBatteryLevel() {
            Log.i(TAG, "Notifying embedded MongoDB of low host battery level");
            for (final MongoClient client : getLocalInstances()) {
              try {
                client
                    .getDatabase(ADMIN_DATABASE_NAME)
                    .runCommand(
                        new Document(
                            BatteryLevelCommand.MONGO_COMMAND,
                            BatteryLevelCommand.BATTERY_LEVEL_LOW));
              } catch (Exception e) {
                Log.w(
                    TAG,
                    "Could not notify embedded MongoDB of low host battery level: "
                        + e.getLocalizedMessage());
              }
            }
          }

          @Override
          public void onOkayBatteryLevel() {
            Log.i(TAG, "Notifying embedded MongoDB of normal host battery level");
            for (final MongoClient client : getLocalInstances()) {
              try {
                client
                    .getDatabase(ADMIN_DATABASE_NAME)
                    .runCommand(
                        new Document(
                            BatteryLevelCommand.MONGO_COMMAND,
                            BatteryLevelCommand.BATTERY_LEVEL_NORMAL));
              } catch (Exception e) {
                Log.w(
                    TAG,
                    "Could not notify embedded MongoDB of normal host battery level: "
                        + e.getLocalizedMessage());
              }
            }
          }

          @Override
          public void onTrimMemory(final String memoryTrimMode) {
            Log.i(TAG, "Notifying embedded MongoDB of low memory condition on host");
            for (final MongoClient client : getLocalInstances()) {
              try {
                client
                    .getDatabase(ADMIN_DATABASE_NAME)
                    .runCommand(
                        new Document(TrimMemoryCommand.MONGO_COMMAND, memoryTrimMode));
              } catch (Exception e) {
                Log.w(
                    TAG,
                    "Could not notify embedded MongoDB of low memory condition on host: "
                        + e.getLocalizedMessage());
              }
            }
          }
        });
  }

  private static final class BatteryLevelCommand {
    private static final String MONGO_COMMAND = "setBatteryLevel";
    private static final String BATTERY_LEVEL_LOW = "low";
    private static final String BATTERY_LEVEL_NORMAL = "normal";
  }

  private static final class TrimMemoryCommand {
    private static final String MONGO_COMMAND = "trimMemory";
  }
}
