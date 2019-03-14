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

package com.mongodb.stitch.android.services.mongodb.remote;

import org.bson.Document;

/**
 * The RemoteMongoDatabase interface provides access to its {@link RemoteMongoCollection}s.
 * <p>
 * In order to obtain an instance of the database, use {@link RemoteMongoClient#getDatabase}.
 * </p><p>
 * Once you have a database, you can use {@link getCollection} to get a collection
 * to start reading and writing documents.
 * </p>
 * @see RemoteMongoClient
 * @see RemoteMongoCollection
 */
public interface RemoteMongoDatabase {

  /**
   * Gets the name of the database.
   *
   * @return the database name
   */
  String getName();

  /**
   * Gets a {@link RemoteMongoCollection}.
   *
   * @param collectionName the name of the collection to return
   * @return the collection
   */
  RemoteMongoCollection<Document> getCollection(final String collectionName);

  /**
   * Gets a collection, with a specific default document class.
   *
   * @param collectionName the name of the collection to return
   * @param documentClass  the default class to cast any documents returned from the database into.
   * @param <DocumentT>    the type of the class to use instead of {@code Document}.
   * @return the collection
   */
  <DocumentT> RemoteMongoCollection<DocumentT> getCollection(
      final String collectionName,
      final Class<DocumentT> documentClass
  );
}
