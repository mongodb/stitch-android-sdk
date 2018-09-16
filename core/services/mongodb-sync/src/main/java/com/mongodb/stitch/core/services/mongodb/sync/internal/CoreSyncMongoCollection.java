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

import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoCollection;
import com.mongodb.stitch.core.services.mongodb.sync.CoreSync;
import org.bson.codecs.configuration.CodecRegistry;

// TODO: Seamless offline CRUD parity.
public interface CoreSyncMongoCollection<DocumentT> extends CoreRemoteMongoCollection<DocumentT> {

  /**
   * Create a new CoreSyncMongoCollection instance with a different default class to cast any
   * documents returned from the database into.
   *
   * @param clazz the default class to cast any documents returned from the database into.
   * @param <NewDocumentT> the type that the new collection will encode documents from and decode
   *                      documents to.
   * @return a new CoreSyncMongoCollection instance with the different default class
   */
  <NewDocumentT> CoreSyncMongoCollection<NewDocumentT> withDocumentClass(
      final Class<NewDocumentT> clazz);

  /**
   * Create a new CoreSyncMongoCollection instance with a different codec registry.
   *
   * @param codecRegistry the new {@link org.bson.codecs.configuration.CodecRegistry} for the
   *                      collection.
   * @return a new CoreSyncMongoCollection instance with the different codec registry
   */
  CoreSyncMongoCollection<DocumentT> withCodecRegistry(final CodecRegistry codecRegistry);

  /**
   * A set of synchronization related operations at the collection level.
   *
   * @return a set of sync related operations
   */
  CoreSync<DocumentT> sync();
}
