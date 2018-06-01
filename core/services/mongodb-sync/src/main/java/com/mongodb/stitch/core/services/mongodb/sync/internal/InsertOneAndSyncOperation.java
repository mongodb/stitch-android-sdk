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
import com.mongodb.stitch.core.internal.common.BsonUtils;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertOneResult;
import com.mongodb.stitch.core.services.mongodb.remote.internal.Operation;
import com.mongodb.stitch.core.services.mongodb.sync.ChangeEventListener;
import com.mongodb.stitch.core.services.mongodb.sync.SyncConflictResolver;
import javax.annotation.Nullable;
import org.bson.BsonDocument;
import org.bson.codecs.Codec;

class InsertOneAndSyncOperation<T> implements Operation<RemoteInsertOneResult> {

  private final MongoNamespace namespace;
  private final BsonDocument document;
  private final DataSynchronizer dataSynchronizer;
  private final SyncConflictResolver<T> conflictResolver;
  private final ChangeEventListener<T> eventListener;
  private final Codec<T> documentCodec;

  InsertOneAndSyncOperation(
      final MongoNamespace namespace,
      final BsonDocument document,
      final DataSynchronizer dataSynchronizer,
      final SyncConflictResolver<T> conflictResolver,
      final ChangeEventListener<T> eventListener,
      final Codec<T> documentCodec
  ) {
    this.namespace = namespace;
    this.document = document;
    this.dataSynchronizer = dataSynchronizer;
    this.conflictResolver = conflictResolver;
    this.eventListener = eventListener;
    this.documentCodec = documentCodec;
  }

  public RemoteInsertOneResult execute(@Nullable final CoreStitchServiceClient service) {
    this.dataSynchronizer.insertOneAndSync(
        namespace, document, conflictResolver, eventListener, documentCodec);
    return new RemoteInsertOneResult(BsonUtils.getDocumentId(document));
  }
}
