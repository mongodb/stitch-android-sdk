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

package com.mongodb.stitch.core.services.mongodb.sync.internal;

import com.mongodb.MongoNamespace;
import com.mongodb.client.MongoDatabase;
import com.mongodb.stitch.core.internal.net.NetworkMonitor;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoDatabase;
import java.util.HashSet;
import java.util.Set;
import org.bson.Document;

public class CoreSyncMongoDatabaseImpl implements CoreSyncMongoDatabase {

  private final CoreRemoteMongoDatabase proxy;
  private final DataSynchronizer dataSynchronizer;
  private final NetworkMonitor networkMonitor;
  private final CoreStitchServiceClient service;
  private final MongoDatabase tempDb;

  CoreSyncMongoDatabaseImpl(
      final CoreRemoteMongoDatabase proxy,
      final DataSynchronizer dataSynchronizer,
      final NetworkMonitor networkMonitor,
      final CoreStitchServiceClient service,
      final MongoDatabase tempDb
  ) {
    this.proxy = proxy;
    this.dataSynchronizer = dataSynchronizer;
    this.networkMonitor = networkMonitor;
    this.service = service;
    this.tempDb = tempDb;
  }

  /**
   * Gets the name of the database.
   *
   * @return the database name
   */
  public String getName() {
    return proxy.getName();
  }

  /**
   * Gets a collection.
   *
   * @param collectionName the name of the collection to return
   * @return the collection
   */
  public CoreSyncMongoCollectionImpl<Document> getCollection(final String collectionName) {
    return new CoreSyncMongoCollectionImpl<>(
        proxy.getCollection(collectionName),
        dataSynchronizer,
        networkMonitor,
        service,
        tempDb);
  }

  /**
   * Gets a collection, with a specific default document class.
   *
   * @param collectionName the name of the collection to return
   * @param documentClass  the default class to cast any documents returned from the database into.
   * @param <DocumentT>    the type of the class to use instead of {@code Document}.
   * @return the collection
   */
  public <DocumentT> CoreSyncMongoCollectionImpl<DocumentT> getCollection(
      final String collectionName,
      final Class<DocumentT> documentClass
  ) {
    return new CoreSyncMongoCollectionImpl<>(
        proxy.getCollection(collectionName, documentClass),
        dataSynchronizer,
        networkMonitor,
        service,
        tempDb);
  }

  /**
   * Returns the set of synchronized collections.
   *
   * @return the set of synchronized collections.
   */
  @Override
  public Set<String> getSynchronizedCollections() {
    final Set<String> colls = new HashSet<>();
    for (final MongoNamespace ns : this.dataSynchronizer.getSynchronizedNamespaces()) {
      colls.add(ns.getCollectionName());
    }
    return colls;
  }
}
