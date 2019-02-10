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

package com.mongodb.stitch.core.services.mongodb.remote.internal;

import static com.mongodb.MongoNamespace.checkDatabaseNameValidity;
import static com.mongodb.stitch.core.internal.common.Assertions.notNull;

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.internal.net.NetworkMonitor;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.DataSynchronizer;

import org.bson.Document;

public class CoreRemoteMongoDatabaseImpl implements CoreRemoteMongoDatabase {

  private final String name;
  private final CoreStitchServiceClient service;

  // Sync related fields
  private final DataSynchronizer dataSynchronizer;
  private final NetworkMonitor networkMonitor;

  CoreRemoteMongoDatabaseImpl(
      final String name,
      final CoreStitchServiceClient service,
      final DataSynchronizer dataSynchronizer,
      final NetworkMonitor networkMonitor
  ) {
    notNull("name", name);
    checkDatabaseNameValidity(name);
    this.name = name;
    this.service = service;
    this.dataSynchronizer = dataSynchronizer;
    this.networkMonitor = networkMonitor;
  }

  /**
   * Gets the name of the database.
   *
   * @return the database name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets a collection.
   *
   * @param collectionName the name of the collection to return
   * @return the collection
   */
  public CoreRemoteMongoCollectionImpl<Document> getCollection(final String collectionName) {
    return getCollection(collectionName, Document.class);
  }

  /**
   * Gets a collection, with a specific default document class.
   *
   * @param collectionName the name of the collection to return
   * @param documentClass  the default class to cast any documents returned from the database into.
   * @param <DocumentT>    the type of the class to use instead of {@code Document}.
   * @return the collection
   */
  public <DocumentT> CoreRemoteMongoCollectionImpl<DocumentT> getCollection(
      final String collectionName,
      final Class<DocumentT> documentClass
  ) {
    return new CoreRemoteMongoCollectionImpl<>(
        new MongoNamespace(name, collectionName),
        documentClass,
        service,
        dataSynchronizer,
        networkMonitor
    );
  }
}
