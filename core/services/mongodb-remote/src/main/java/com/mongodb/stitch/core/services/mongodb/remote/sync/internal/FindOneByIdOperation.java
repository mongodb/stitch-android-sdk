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
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoCollection;
import com.mongodb.stitch.core.services.mongodb.remote.internal.Operation;

import javax.annotation.Nonnull;
import org.bson.BsonDocument;
import org.bson.BsonValue;

class FindOneByIdOperation<T> implements Operation<T> {

  private final MongoNamespace namespace;
  private final BsonValue documentId;
  private final Class<T> resultClass;
  private final DataSynchronizer dataSynchronizer;

  FindOneByIdOperation(
      final MongoNamespace namespace,
      final BsonValue documentId,
      final Class<T> resultClass,
      final DataSynchronizer dataSynchronizer
  ) {
    this.namespace = namespace;
    this.documentId = documentId;
    this.resultClass = resultClass;
    this.dataSynchronizer = dataSynchronizer;
  }

  public T execute(@Nonnull final CoreStitchServiceClient service) {
    return this.dataSynchronizer.findOneById(
      namespace,
      documentId,
      resultClass,
      service.getCodecRegistry());
  }
}
