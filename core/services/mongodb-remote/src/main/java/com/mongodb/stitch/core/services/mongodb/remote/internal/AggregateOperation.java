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
import com.mongodb.stitch.core.services.internal.CoreStitchService;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.codecs.Decoder;

class AggregateOperation<T> implements Operation<Collection<T>> {

  private final MongoNamespace namespace;
  private final List<BsonDocument> pipeline;
  private final Decoder<T> decoder;

  /**
   * Construct a new instance.
   *
   * @param namespace the database and collection namespace for the operation.
   * @param pipeline the aggregation pipeline.
   * @param decoder the decoder for the result documents.
   */
  AggregateOperation(
      final MongoNamespace namespace,
      final List<BsonDocument> pipeline,
      final Decoder<T> decoder
  ) {
    notNull("namespace", namespace);
    notNull("pipeline", pipeline);
    notNull("decoder", decoder);
    this.namespace = namespace;
    this.pipeline = pipeline;
    this.decoder = decoder;
  }

  public Collection<T> execute(final CoreStitchService service) {
    final Document args = new Document();
    args.put("database", namespace.getDatabaseName());
    args.put("collection", namespace.getCollectionName());
    args.put("pipeline", pipeline);

    return service.callFunctionInternal(
        "aggregate",
        Collections.singletonList(args),
        new CollectionDecoder<>(decoder));
  }
}
