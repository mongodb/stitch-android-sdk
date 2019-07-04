/*
 * Copyright 2018-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.stitch.core.services.mongodb.remote.internal;

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.internal.net.Stream;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.BaseChangeEvent;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.Decoder;

@SuppressWarnings("unchecked")
public class WatchOperation<DocumentT> {
  private final MongoNamespace namespace;
  private final BsonDocument matchFilter;
  private final Set<BsonValue> ids;
  private final boolean useCompactEvents;
  private final Codec<DocumentT> fullDocumentCodec;

  /**
   * Constructor for full-collection watch.
   */
  WatchOperation(
      final MongoNamespace namespace,
      final boolean useCompactEvents,
      final Codec<DocumentT> fullDocumentCodec
  ) {
    this.namespace = namespace;
    this.matchFilter = null;
    this.ids = null;
    this.useCompactEvents = useCompactEvents;
    this.fullDocumentCodec = fullDocumentCodec;
  }

  /**
   * Constructor for filter-based watch.
   */
  WatchOperation(
      final MongoNamespace namespace,
      final BsonDocument matchFilter,
      final boolean useCompactEvents,
      final Codec<DocumentT> fullDocumentCodec
  ) {
    this.namespace = namespace;
    this.matchFilter = matchFilter;
    this.ids = null;
    this.useCompactEvents = useCompactEvents;
    this.fullDocumentCodec = fullDocumentCodec;
  }

  /**
   * Constructor id-based watch.
   */
  WatchOperation(
      final MongoNamespace namespace,
      final Set<BsonValue> ids,
      final boolean useCompactEvents,
      final Codec<DocumentT> fullDocumentCodec
  ) {
    this.namespace = namespace;
    this.matchFilter = null;
    this.ids = ids;
    this.useCompactEvents = useCompactEvents;
    this.fullDocumentCodec = fullDocumentCodec;
  }

  public <ChangeEventT extends BaseChangeEvent<DocumentT>> Stream<ChangeEventT> execute(
      final CoreStitchServiceClient service
  ) throws InterruptedException, IOException {
    final Document args = new Document();
    args.put("database", namespace.getDatabaseName());
    args.put("collection", namespace.getCollectionName());
    args.put("useCompactEvents", useCompactEvents);

    if (ids != null) {
      args.put("ids", ids);
    } else if (matchFilter != null) {
      args.put("filter", matchFilter);
    }

    final Decoder<ChangeEventT> decoder = useCompactEvents
        ? (Decoder<ChangeEventT>) ResultDecoders.compactChangeEventDecoder(fullDocumentCodec)
        : (Decoder<ChangeEventT>) ResultDecoders.changeEventDecoder(fullDocumentCodec);

    return service.streamFunction(
        "watch",
        Collections.singletonList(args),
        decoder);
  }
}
