/*
 * Copyright 2019-present MongoDB, Inc.
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
import com.mongodb.stitch.core.services.mongodb.remote.ChangeEvent;

import java.util.Collections;
import java.util.Set;

import org.bson.BsonValue;
import org.bson.Document;
import org.bson.codecs.Codec;

public class WatchOperation<DocumentT> {
  private final MongoNamespace namespace;
  private final Set<BsonValue> ids;
  private final Codec<DocumentT> fullDocumentCodec;

  WatchOperation(
      final MongoNamespace namespace,
      final Set<BsonValue> ids,
      final Codec<DocumentT> fullDocumentCodec
  ) {
    this.namespace = namespace;
    this.ids = ids;
    this.fullDocumentCodec = fullDocumentCodec;
  }

  public Stream<ChangeEvent<DocumentT>> execute(final CoreStitchServiceClient service) {
    final Document args = new Document();
    args.put("database", namespace.getDatabaseName());
    args.put("collection", namespace.getCollectionName());
    args.put("ids", ids);

    return service.streamFunction(
        "watch",
        Collections.singletonList(args),
        ResultDecoders.changeEventDecoder(fullDocumentCodec));
  }
}
