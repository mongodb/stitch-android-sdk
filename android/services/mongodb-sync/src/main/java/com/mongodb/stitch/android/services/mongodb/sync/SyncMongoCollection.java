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

import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection;
import org.bson.codecs.configuration.CodecRegistry;

/**
 * The SyncMongoCollection interface.
 *
 * @param <DocumentT> The type that this collection will encode documents from and decode documents
 *                   to.
 */
public interface SyncMongoCollection<DocumentT> extends RemoteMongoCollection<DocumentT> {

  /**
   * Create a new SyncMongoCollection instance with a different default class to cast any
   * documents returned from the database into.
   *
   * @param clazz the default class to cast any documents returned from the database into.
   * @param <NewDocumentT> The type that the new collection will encode documents from and decode
   *                      documents to.
   * @return a new SyncMongoCollection instance with the different default class
   */
  <NewDocumentT> SyncMongoCollection<NewDocumentT> withDocumentClass(
      final Class<NewDocumentT> clazz);

  /**
   * Create a new SyncMongoCollection instance with a different codec registry.
   *
   * @param codecRegistry the new {@link org.bson.codecs.configuration.CodecRegistry} for the
   *                      collection.
   * @return a new SyncMongoCollection instance with the different codec registry
   */
  SyncMongoCollection<DocumentT> withCodecRegistry(final CodecRegistry codecRegistry);

  /**
   * A set of synchronization related operations at the collection level.
   *
   * @return a set of sync related operations
   */
  Sync<DocumentT> sync();
}
