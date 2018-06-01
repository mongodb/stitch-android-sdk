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

package com.mongodb.stitch.android.services.mongodb.sync;

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.android.core.services.internal.NamedServiceClientFactory;
import com.mongodb.stitch.android.core.services.internal.StitchServiceClient;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient;
import com.mongodb.stitch.android.services.mongodb.sync.internal.SyncMongoClientImpl;
import com.mongodb.stitch.core.StitchAppClientInfo;
import com.mongodb.stitch.core.services.mongodb.sync.internal.SyncClientFactory;
import java.io.Closeable;
import java.util.Set;

/**
 * The remote MongoClient used for working with data in MongoDB remotely
 * via Stitch that can be synced in real-time and support offline operations.
 */
public interface SyncMongoClient extends RemoteMongoClient, Closeable {

  /**
   * Gets a {@link SyncMongoDatabase} instance for the given database name.
   *
   * @param databaseName the name of the database to retrieve
   * @return a {@code SyncMongoDatabase} representing the specified database
   */
  SyncMongoDatabase getDatabase(final String databaseName);

  /**
   * Returns the set of synchronized namespaces.
   *
   * @return the set of synchronized namespaces.
   */
  Set<MongoNamespace> getSynchronizedNamespaces();

  /**
   * Returns the set of synchronized databases.
   *
   * @return the set of synchronized databases.
   */
  Set<String> getSynchronizedDatabases();

  NamedServiceClientFactory<SyncMongoClient> Factory =
      new NamedServiceClientFactory<SyncMongoClient>() {
        @Override
        public SyncMongoClient getClient(
            final StitchServiceClient service,
            final StitchAppClientInfo appInfo,
            final TaskDispatcher dispatcher
        ) {
          return new SyncMongoClientImpl(
              SyncClientFactory.getClient(service, appInfo), dispatcher);
        }
      };
}
