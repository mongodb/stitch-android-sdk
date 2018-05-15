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
import com.mongodb.stitch.core.services.internal.CoreStitchService;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult;
import java.util.Collections;
import org.bson.BsonDocument;
import org.bson.Document;

class DeleteManyOperation implements Operation<RemoteDeleteResult> {

  private final MongoNamespace namespace;
  private final BsonDocument filter;

  DeleteManyOperation(
      final MongoNamespace namespace,
      final BsonDocument filter
  ) {
    this.namespace = namespace;
    this.filter = filter;
  }

  @Override
  public RemoteDeleteResult execute(final CoreStitchService service) {
    final Document args = new Document();
    args.put("database", namespace.getDatabaseName());
    args.put("collection", namespace.getCollectionName());
    args.put("query", filter);

    return service.callFunctionInternal(
        "deleteMany",
        Collections.singletonList(args),
        ResultDecoders.deleteResultDecoder);
  }
}
