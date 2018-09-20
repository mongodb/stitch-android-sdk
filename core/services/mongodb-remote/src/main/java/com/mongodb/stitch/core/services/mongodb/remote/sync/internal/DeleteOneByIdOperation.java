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
import com.mongodb.client.result.DeleteResult;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult;
import com.mongodb.stitch.core.services.mongodb.remote.internal.Operation;
import javax.annotation.Nullable;
import org.bson.BsonValue;

class DeleteOneByIdOperation implements Operation<RemoteDeleteResult> {

  private final MongoNamespace namespace;
  private final BsonValue documentId;
  private final DataSynchronizer dataSynchronizer;

  DeleteOneByIdOperation(
      final MongoNamespace namespace,
      final BsonValue documentId,
      final DataSynchronizer dataSynchronizer
  ) {
    this.namespace = namespace;
    this.documentId = documentId;
    this.dataSynchronizer = dataSynchronizer;
  }

  public RemoteDeleteResult execute(@Nullable final CoreStitchServiceClient service) {
    final DeleteResult localResult =
        this.dataSynchronizer.deleteOneById(namespace, documentId);
    if (localResult.getDeletedCount() == 1) {
      return new RemoteDeleteResult(localResult.getDeletedCount());
    }
    return new RemoteDeleteResult(0);
  }
}
