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
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.internal.Operation;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncUpdateOptions;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncUpdateResult;

import javax.annotation.Nullable;
import org.bson.BsonDocument;
import org.bson.conversions.Bson;

class UpdateOneOperation implements Operation<SyncUpdateResult> {

  private final MongoNamespace namespace;
  private final Bson filter;
  private final BsonDocument update;
  private final DataSynchronizer dataSynchronizer;
  private final SyncUpdateOptions syncUpdateOptions;

  UpdateOneOperation(
      final MongoNamespace namespace,
      final Bson filter,
      final BsonDocument update,
      final DataSynchronizer dataSynchronizer,
      final SyncUpdateOptions syncUpdateOptions
  ) {
    this.namespace = namespace;
    this.filter = filter;
    this.update = update;
    this.dataSynchronizer = dataSynchronizer;
    this.syncUpdateOptions = syncUpdateOptions;
  }

  public SyncUpdateResult execute(@Nullable final CoreStitchServiceClient service) {
    final UpdateResult localResult = this.dataSynchronizer.updateOne(
        namespace,
        filter,
        update,
        new UpdateOptions().upsert(this.syncUpdateOptions.isUpsert()));

    return new SyncUpdateResult(
        localResult.getMatchedCount(),
        localResult.getModifiedCount(),
        localResult.getUpsertedId()
    );
  }
}
