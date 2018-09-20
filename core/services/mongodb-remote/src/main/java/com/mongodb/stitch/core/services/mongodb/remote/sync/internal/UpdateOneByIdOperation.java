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
import com.mongodb.client.result.UpdateResult;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult;
import com.mongodb.stitch.core.services.mongodb.remote.internal.Operation;
import javax.annotation.Nullable;
import org.bson.BsonDocument;
import org.bson.BsonValue;

class UpdateOneByIdOperation<T> implements Operation<RemoteUpdateResult> {

  private final MongoNamespace namespace;
  private final BsonValue documentId;
  private final BsonDocument update;
  private final DataSynchronizer dataSynchronizer;

  UpdateOneByIdOperation(
      final MongoNamespace namespace,
      final BsonValue documentId,
      final BsonDocument update,
      final DataSynchronizer dataSynchronizer
  ) {
    this.namespace = namespace;
    this.documentId = documentId;
    this.update = update;
    this.dataSynchronizer = dataSynchronizer;
  }

  public RemoteUpdateResult execute(@Nullable final CoreStitchServiceClient service) {
    final UpdateResult localResult =
        this.dataSynchronizer.updateOneById(namespace, documentId, update);
    if (localResult.getMatchedCount() == 1) {
      return new RemoteUpdateResult(
          localResult.getMatchedCount(),
          localResult.getModifiedCount(),
          null);
    }

    return new RemoteUpdateResult(0, 0, null);
  }
}
