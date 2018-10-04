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

package com.mongodb.stitch.server.services.mongodb.remote.internal;

import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoClient;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoClientImpl;
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.DataSynchronizer;
import com.mongodb.stitch.server.services.mongodb.remote.RemoteMongoClient;
import com.mongodb.stitch.server.services.mongodb.remote.RemoteMongoDatabase;

public final class RemoteMongoClientImpl implements RemoteMongoClient {

  private final CoreRemoteMongoClient proxy;

  public RemoteMongoClientImpl(final CoreRemoteMongoClient client) {
    this.proxy = client;
  }

  /**
   * Gets a {@link RemoteMongoDatabase} instance for the given database name.
   *
   * @param databaseName the name of the database to retrieve
   * @return a {@code RemoteMongoDatabase} representing the specified database
   */
  public RemoteMongoDatabase getDatabase(final String databaseName) {
    return new RemoteMongoDatabaseImpl(proxy.getDatabase(databaseName));
  }

  DataSynchronizer getDataSynchronizer() {
    return ((CoreRemoteMongoClientImpl)this.proxy).getDataSynchronizer();
  }
}
