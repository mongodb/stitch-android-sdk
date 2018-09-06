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

package com.mongodb.stitch.android.services.mongodb.sync.internal;

import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.android.services.mongodb.sync.SyncMongoCollection;
import com.mongodb.stitch.android.services.mongodb.sync.SyncMongoDatabase;
import com.mongodb.stitch.core.services.mongodb.sync.internal.CoreSyncMongoDatabase;
import java.util.Set;
import org.bson.Document;

public final class SyncMongoDatabaseImpl implements SyncMongoDatabase {

  private final CoreSyncMongoDatabase proxy;
  private final TaskDispatcher dispatcher;

  SyncMongoDatabaseImpl(
      final CoreSyncMongoDatabase database,
      final TaskDispatcher dispatcher
  ) {
    this.proxy = database;
    this.dispatcher = dispatcher;
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
  public SyncMongoCollection<Document> getCollection(final String collectionName) {
    return new SyncMongoCollectionImpl<>(
        proxy.getCollection(collectionName), dispatcher);
  }

  /**
   * Gets a collection, with a specific default document class.
   *
   * @param collectionName the name of the collection to return
   * @param documentClass  the default class to cast any documents returned from the database into.
   * @param <DocumentT>    the type of the class to use instead of {@code Document}.
   * @return the collection
   */
  public <DocumentT> SyncMongoCollection<DocumentT> getCollection(
      final String collectionName,
      final Class<DocumentT> documentClass
  ) {
    return new SyncMongoCollectionImpl<>(
        proxy.getCollection(collectionName, documentClass), dispatcher);
  }

  /**
   * Returns the set of synchronized collections.
   *
   * @return the set of synchronized collections.
   */
  @Override
  public Set<String> getSynchronizedCollections() {
    return proxy.getSynchronizedCollections();
  }
}
