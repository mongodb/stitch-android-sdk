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
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;

import java.util.Collections;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.codecs.Decoder;

public class FindOneAndModifyOperation<T> implements Operation<T> {

  private final MongoNamespace namespace;
  private final Decoder<T> decoder;
  private final String methodName;
  private final BsonDocument filter;
  private final BsonDocument update;
  private final BsonDocument project;
  private final BsonDocument sort;
  private boolean upsert;
  private boolean returnNewDocument;

  /**
   * Construct a new instance.
   *
   * @param namespace the database and collection namespace for the operation.
   * @param methodName the name of the findOneAndModify function to run.
   * @param filter the filter to query for the document.
   * @param update the update to apply to the resulting document.
   * @param project the projection operation to apply to the returned document.
   * @param sort the sort to use on the query before selecting the first document.
   * @param decoder the decoder for the result documents.Operations.java
   *
   */
  FindOneAndModifyOperation(
          final MongoNamespace namespace,
          final String methodName,
          final BsonDocument filter,
          final BsonDocument update,
          final BsonDocument project,
          final BsonDocument sort,
          final Decoder<T> decoder) {
    notNull("namespace", namespace);
    notNull("methodName", methodName);
    notNull("filter", filter);
    notNull("update", update);
    notNull("decoder", decoder);
    this.namespace = namespace;
    this.methodName = methodName;
    this.filter = filter;
    this.update = update;
    this.project = project;
    this.sort = sort;
    this.decoder = decoder;
  }

  public FindOneAndModifyOperation upsert(final boolean upsert) {
    this.upsert = upsert;
    return this;
  }

  public FindOneAndModifyOperation returnNewDocument(final boolean returnNewDocument) {
    this.returnNewDocument = returnNewDocument;
    return this;
  }

  public T execute(final CoreStitchServiceClient service) {

    final Document args = new Document();
    args.put("database", namespace.getDatabaseName());
    args.put("collection", namespace.getCollectionName());
    args.put("filter", filter);

    // Send project and sort if they are not null
    if (project != null) {
      args.put("projection", project);
    }
    if (sort != null) {
      args.put("sort", sort);
    }

    // findOneAndDelete() does not take these arguments
    if (!methodName.equals("findOneAndDelete")) {
      args.put("update", update);

      if (upsert) {
        args.put("upsert", true);
      }
      if (returnNewDocument) {
        args.put("returnNewDocument", true);
      }
    }

    return service.callFunction(methodName, Collections.singletonList(args), decoder);
  }
}
