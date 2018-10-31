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
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncInsertManyResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.bson.BsonDocument;
import org.bson.BsonValue;

class InsertManyAndSyncOperation implements Operation<SyncInsertManyResult> {

  private final MongoNamespace namespace;
  private final List<BsonDocument> documents;
  private final DataSynchronizer dataSynchronizer;

  InsertManyAndSyncOperation(
      final MongoNamespace namespace,
      final List<BsonDocument> documents,
      final DataSynchronizer dataSynchronizer
  ) {
    this.namespace = namespace;
    this.documents = documents;
    this.dataSynchronizer = dataSynchronizer;
  }

  public SyncInsertManyResult execute(@Nullable final CoreStitchServiceClient service) {
    this.dataSynchronizer.insertManyAndSync(namespace, documents);
    final Map<Long, BsonValue> indexToId = new HashMap<>();
    for (int i = 0; i < this.documents.size(); i++) {
      indexToId.put((long)i, BsonUtils.getDocumentId(this.documents.get(i)));
    }
    return new SyncInsertManyResult(indexToId);
  }
}
