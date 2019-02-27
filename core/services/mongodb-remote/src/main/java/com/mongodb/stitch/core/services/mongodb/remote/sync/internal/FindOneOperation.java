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

package com.mongodb.stitch.core.services.mongodb.remote.sync.internal;

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.internal.Operation;

import javax.annotation.Nullable;

import org.bson.BsonDocument;

import static com.mongodb.stitch.core.internal.common.Assertions.notNull;

class FindOneOperation<T> implements Operation<T> {

  private final MongoNamespace namespace;
  private final Class<T> resultClass;
  private final BsonDocument projection;
  private final BsonDocument sort;
  private final BsonDocument filter;
  private final DataSynchronizer dataSynchronizer;

  FindOneOperation(
          final MongoNamespace namespace,
          final Class<T> resultClass,
          final BsonDocument filter,
          final BsonDocument projection,
          final BsonDocument sort,
          final DataSynchronizer dataSynchronizer) {
    notNull("namespace", namespace);
    notNull("filter", filter);
    notNull("dataSynchronizer", dataSynchronizer);
    notNull("resultClass", resultClass);
    this.namespace = namespace;
    this.resultClass = resultClass;
    this.filter = filter;
    this.projection = projection;
    this.sort = sort;
    this.dataSynchronizer = dataSynchronizer;
  }

  public T execute(@Nullable final CoreStitchServiceClient service) {
    return this.dataSynchronizer.findOne(
            namespace,
            filter,
            projection,
            sort,
            resultClass,
            service.getCodecRegistry());
  }
}
