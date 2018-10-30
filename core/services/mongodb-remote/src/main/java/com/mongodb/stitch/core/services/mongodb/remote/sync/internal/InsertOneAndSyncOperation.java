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
import com.mongodb.stitch.core.internal.common.BsonUtils;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.internal.Operation;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncInsertOneResult;

import javax.annotation.Nullable;
import org.bson.BsonDocument;

class InsertOneAndSyncOperation implements Operation<SyncInsertOneResult> {

  private final MongoNamespace namespace;
  private final BsonDocument document;
  private final DataSynchronizer dataSynchronizer;

  InsertOneAndSyncOperation(
      final MongoNamespace namespace,
      final BsonDocument document,
      final DataSynchronizer dataSynchronizer
  ) {
    this.namespace = namespace;
    this.document = document;
    this.dataSynchronizer = dataSynchronizer;
  }

  public SyncInsertOneResult execute(@Nullable final CoreStitchServiceClient service) {
    this.dataSynchronizer.insertOneAndSync(namespace, document);
    return new SyncInsertOneResult(BsonUtils.getDocumentId(document));
  }
}
