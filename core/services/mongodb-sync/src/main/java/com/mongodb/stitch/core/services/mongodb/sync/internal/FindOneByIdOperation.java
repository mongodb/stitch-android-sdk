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

package com.mongodb.stitch.core.services.mongodb.sync.internal;

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.internal.net.NetworkMonitor;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoCollection;
import com.mongodb.stitch.core.services.mongodb.remote.internal.Operation;
import javax.annotation.Nullable;
import org.bson.BsonDocument;
import org.bson.BsonValue;

class FindOneByIdOperation<T> implements Operation<T> {

  private final MongoNamespace namespace;
  private final BsonValue documentId;
  private final Class<T> resultClass;
  private final DataSynchronizer dataSynchronizer;
  private final NetworkMonitor networkMonitor;
  private final CoreRemoteMongoCollection<T> remoteCollection;

  FindOneByIdOperation(
      final MongoNamespace namespace,
      final BsonValue documentId,
      final Class<T> resultClass,
      final DataSynchronizer dataSynchronizer,
      final NetworkMonitor networkMonitor,
      final CoreRemoteMongoCollection<T> remoteCollection
  ) {
    this.namespace = namespace;
    this.documentId = documentId;
    this.resultClass = resultClass;
    this.dataSynchronizer = dataSynchronizer;
    this.networkMonitor = networkMonitor;
    this.remoteCollection = remoteCollection;
  }

  public T execute(@Nullable final CoreStitchServiceClient service) {
    final T localDocument =
        this.dataSynchronizer.findOneById(
            namespace, documentId, resultClass, service.getCodecRegistry());
    if (localDocument != null || !this.networkMonitor.isConnected()) {
      return localDocument;
    }
    final BsonDocument filter = new BsonDocument("_id", documentId);
    return remoteCollection.find(filter).first();
  }
}
