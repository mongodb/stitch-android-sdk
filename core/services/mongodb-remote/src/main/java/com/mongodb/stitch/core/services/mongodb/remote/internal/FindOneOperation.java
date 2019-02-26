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

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteFindOptions;

import java.util.Collections;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.codecs.Decoder;
import org.bson.conversions.Bson;

import static com.mongodb.stitch.core.internal.common.Assertions.notNull;

public class FindOneOperation<T> implements Operation<T> {

  private final MongoNamespace namespace;
  private final Decoder<T> decoder;
  private final BsonDocument projection;
  private final BsonDocument sort;
  private final BsonDocument filter;

  /**
   * Construct a new instance.
   *
   * @param namespace the database and collection namespace for the operation.
   * @param decoder the decoder for the result documents.
   */
  FindOneOperation(
          final MongoNamespace namespace,
          final BsonDocument filter,
          final BsonDocument projection,
          final BsonDocument sort,
          final Decoder<T> decoder) {
    notNull("namespace", namespace);
    notNull("decoder", decoder);
    notNull("filter", filter);
    this.namespace = namespace;
    this.filter = filter;
    this.projection = projection;
    this.sort = sort;
    this.decoder = decoder;
  }

  public T execute(final CoreStitchServiceClient service) {

    final Document args = new Document();
    args.put("database", namespace.getDatabaseName());
    args.put("collection", namespace.getCollectionName());
    args.put("query", filter);

    if (projection != null) {
      args.put("project", projection);
    }
    if (sort != null) {
      args.put("sort", sort);
    }

    return service.callFunction("findOne", Collections.singletonList(args), decoder);
  }
}
