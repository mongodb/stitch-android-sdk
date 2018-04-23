package com.mongodb.stitch.android.services.mongodb.local;

import android.util.Log;

import com.mongodb.client.MongoClient;
import com.mongodb.embedded.client.MongoClientSettings;
import com.mongodb.embedded.client.MongoClients;
import com.mongodb.stitch.android.services.StitchService;
import com.mongodb.stitch.android.services.internal.ServiceClientProvider;
import com.mongodb.stitch.android.services.mongodb.local.internal.MongoDBMobileProvider;
import com.mongodb.stitch.core.StitchAppClientInfo;

import org.bson.Document;

import java.util.HashMap;
import java.util.Map;

public final class LocalMongoDBService {

    private static final String TAG = LocalMongoDBService.class.getSimpleName();

    private static final String ADMIN_DATABASE_NAME = "admin";

    private static final class BatteryLevelCommand {
        private static final String MONGO_COMMAND = "setBatteryLevel";
        private static final String BATTERY_LEVEL_LOW = "low";
        private static final String BATTERY_LEVEL_NORMAL = "normal";
    }

    public static final class TrimMemoryCommand {
        private static final String MONGO_COMMAND = "trimMemory";
    }

    private static final Map<String, MongoClient> localInstances = new HashMap<>();

    public static final ServiceClientProvider<MongoClient> ClientProvider =
            new ServiceClientProvider<MongoClient>() {

        @Override
        public synchronized MongoClient getClient(
            final StitchService stitchService, final StitchAppClientInfo appInfo) {
            final String dataDir = appInfo.dataDirectory;
            if (dataDir == null) {
                throw new IllegalStateException("StitchAppClient not configured with a data directory");
            }
            final String instanceKey = String.format("%s-%s", appInfo.clientAppId, dataDir);
            if (localInstances.containsKey(instanceKey)) {
                return localInstances.get(instanceKey);
            }

            final String dbPath = String.format("%s/local_mongodb/0/", dataDir);
            final MongoClient client = MongoClients.create(MongoClientSettings.builder().
                    dbPath(dbPath).
                    build());

            localInstances.put(instanceKey, client);
            MongoDBMobileProvider.addEventListener(new MongoDBMobileProvider.EventListener() {
                @Override
                public void onLowBatteryLevel() {
                    Log.i(TAG, "Notifying embedded MongoDB of low host battery level");
                    for(final MongoClient client: localInstances.values()) {
                        try {
                            client
                                .getDatabase(ADMIN_DATABASE_NAME)
                                .runCommand(new Document(
                                        BatteryLevelCommand.MONGO_COMMAND,
                                        BatteryLevelCommand.BATTERY_LEVEL_LOW));
                        } catch (Exception e) {
                            Log.w(TAG, "Could not notify embedded MongoDB of low host battery level: " +
                                    e.getLocalizedMessage());
                        }
                    }
                }

                @Override
                public void onOkayBatteryLevel() {
                    Log.i(TAG, "Notifying embedded MongoDB of normal host battery level");
                    for(final MongoClient client: localInstances.values()) {
                        try {
                            client
                                .getDatabase(ADMIN_DATABASE_NAME)
                                .runCommand(new Document(
                                        BatteryLevelCommand.MONGO_COMMAND,
                                        BatteryLevelCommand.BATTERY_LEVEL_NORMAL));
                        } catch (Exception e) {
                            Log.w(TAG, "Could not notify embedded MongoDB of normal host battery level: " +
                                    e.getLocalizedMessage());
                        }
                    }
                }

                @Override
                public void onTrimMemory(final String memoryTrimMode) {
                    Log.i(TAG, "Notifying embedded MongoDB of low memory condition on host");
                    for(final MongoClient client: localInstances.values()) {
                        try {
                            client
                                .getDatabase(ADMIN_DATABASE_NAME)
                                .runCommand(new Document(TrimMemoryCommand.MONGO_COMMAND, memoryTrimMode));
                        } catch (Exception e) {
                            Log.w(TAG, "Could not notify embedded MongoDB of low memory condition on host: " +
                                    e.getLocalizedMessage());
                        }
                    }
                }
            });

            return client;
        }
    };
}
