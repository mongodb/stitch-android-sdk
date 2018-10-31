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
import com.mongodb.stitch.core.services.mongodb.remote.internal.Operation;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncDeleteResult;

import javax.annotation.Nullable;

import org.bson.conversions.Bson;

class DeleteManyOperation implements Operation<SyncDeleteResult> {
  private final MongoNamespace namespace;
  private final Bson filter;
  private final DataSynchronizer dataSynchronizer;

  DeleteManyOperation(
      final MongoNamespace namespace,
      final Bson filter,
      final DataSynchronizer dataSynchronizer
  ) {
    this.namespace = namespace;
    this.filter = filter;
    this.dataSynchronizer = dataSynchronizer;
  }

  public SyncDeleteResult execute(@Nullable final CoreStitchServiceClient service) {
    final DeleteResult localResult = this.dataSynchronizer.deleteMany(namespace, filter);
    return new SyncDeleteResult(localResult.getDeletedCount());
  }
}
