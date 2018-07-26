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

import java.util.Collections;
import org.bson.BsonDocument;
import org.bson.Document;

class CountOperation implements Operation<Long> {

  private final MongoNamespace namespace;
  private BsonDocument filter;
  private int limit;

  CountOperation(final MongoNamespace namespace) {
    this.namespace = namespace;
  }

  public CountOperation filter(final BsonDocument filter) {
    this.filter = filter;
    return this;
  }

  public CountOperation limit(final int limit) {
    this.limit = limit;
    return this;
  }

  public Long execute(final CoreStitchServiceClient service) {
    final Document args = new Document();
    args.put("database", namespace.getDatabaseName());
    args.put("collection", namespace.getCollectionName());
    args.put("query", filter);
    args.put("limit", limit);

    return service.callFunction(
        "count",
        Collections.singletonList(args),
        Long.class);
  }
}
