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

import static com.mongodb.stitch.core.internal.common.Assertions.notNull;

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.internal.common.CollectionDecoder;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;

import java.util.Collection;
import java.util.Collections;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.codecs.Decoder;

class FindOperation<T> implements Operation<Collection<T>> {

  private final MongoNamespace namespace;
  private final Decoder<T> decoder;
  private BsonDocument filter;
  private int limit;
  private BsonDocument projection;
  private BsonDocument sort;

  /**
   * Construct a new instance.
   *
   * @param namespace the database and collection namespace for the operation.
   * @param decoder the decoder for the result documents.
   */
  FindOperation(final MongoNamespace namespace, final Decoder<T> decoder) {
    notNull("namespace", namespace);
    notNull("decoder", decoder);
    this.namespace = namespace;
    this.decoder = decoder;
  }

  /**
   * Sets the query filter to apply to the query.
   *
   * @param filter the filter, which may be null.
   * @return this
   */
  FindOperation<T> filter(final BsonDocument filter) {
    this.filter = filter;
    return this;
  }

  /**
   * Sets the limit to apply.
   *
   * @param limit the limit, which may be null
   * @return this
   */
  FindOperation<T> limit(final int limit) {
    this.limit = limit;
    return this;
  }

  /**
   * Sets a document describing the fields to return for all matching documents.
   *
   * @param projection the project document, which may be null.
   * @return this
   */
  FindOperation<T> projection(final BsonDocument projection) {
    this.projection = projection;
    return this;
  }

  /**
   * Sets the sort criteria to apply to the query.
   *
   * @param sort the sort criteria, which may be null.
   * @return this
   */
  FindOperation<T> sort(final BsonDocument sort) {
    this.sort = sort;
    return this;
  }

  public Collection<T> execute(final CoreStitchServiceClient service) {

    final Document args = new Document();
    args.put("database", namespace.getDatabaseName());
    args.put("collection", namespace.getCollectionName());
    args.put("query", filter);
    args.put("limit", limit);
    args.put("project", projection);
    args.put("sort", sort);

    return service.callFunction(
        "find",
        Collections.singletonList(args),
        new CollectionDecoder<>(decoder));
  }
}
