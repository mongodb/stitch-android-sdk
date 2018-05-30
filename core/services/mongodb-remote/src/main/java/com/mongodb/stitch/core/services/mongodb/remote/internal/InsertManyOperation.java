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
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertManyResult;
import java.util.Collections;
import java.util.List;
import org.bson.BsonDocument;
import org.bson.Document;

class InsertManyOperation implements Operation<RemoteInsertManyResult> {
  private final MongoNamespace namespace;
  private final List<BsonDocument> documents;

  InsertManyOperation(
      final MongoNamespace namespace,
      final List<BsonDocument> documents
  ) {
    this.namespace = namespace;
    this.documents = documents;
  }

  public RemoteInsertManyResult execute(final CoreStitchServiceClient service) {
    final Document args = new Document();
    args.put("database", namespace.getDatabaseName());
    args.put("collection", namespace.getCollectionName());
    args.put("documents", documents);

    return service.callFunctionInternal(
        "insertMany",
        Collections.singletonList(args),
        ResultDecoders.insertManyResultDecoder);
  }
}
