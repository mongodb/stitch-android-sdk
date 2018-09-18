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

import static com.mongodb.stitch.core.internal.common.Assertions.notNull;
import static com.mongodb.stitch.core.internal.common.BsonUtils.documentToBsonDocument;
import static com.mongodb.stitch.core.internal.common.BsonUtils.getCodec;
import static com.mongodb.stitch.core.internal.common.BsonUtils.toBsonDocument;

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.internal.common.BsonUtils;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteFindOptions;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.codecs.CollectibleCodec;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

public class SyncOperations<DocumentT> {

  private final MongoNamespace namespace;
  private final Class<DocumentT> documentClass;
  private final DataSynchronizer dataSynchronizer;
  private final CodecRegistry codecRegistry;

  public SyncOperations(
      final MongoNamespace namespace,
      final Class<DocumentT> documentClass,
      final DataSynchronizer dataSynchronizer,
      final CodecRegistry codecRegistry
  ) {
    this.namespace = namespace;
    this.documentClass = documentClass;
    this.dataSynchronizer = dataSynchronizer;
    this.codecRegistry = codecRegistry;
  }

  <ResultT> SyncFindOperation<ResultT> findFirst(
      final Bson filter,
      final Class<ResultT> resultClass,
      final RemoteFindOptions options
  ) {
    return createSyncFindOperation(namespace, filter, resultClass, options).limit(1);
  }

  <ResultT> SyncFindOperation<ResultT> find(
      final Bson filter,
      final Class<ResultT> resultClass,
      final RemoteFindOptions options
  ) {
    return createSyncFindOperation(namespace, filter, resultClass, options);
  }



  private <ResultT> SyncFindOperation<ResultT> createSyncFindOperation(
      final MongoNamespace findNamespace,
      final Bson filter,
      final Class<ResultT> resultClass,
      final RemoteFindOptions options
  ) {
    final BsonDocument filterDoc = filter.toBsonDocument(documentClass, codecRegistry);
    final BsonDocument projDoc = BsonUtils.toBsonDocumentOrNull(
        options.getProjection(),
        documentClass,
        codecRegistry);
    final BsonDocument sortDoc = BsonUtils.toBsonDocumentOrNull(
        options.getSort(),
        documentClass,
        codecRegistry);
    return new SyncFindOperation<>(
        findNamespace,
        resultClass,
        dataSynchronizer)
        .filter(filterDoc)
        .limit(options.getLimit())
        .projection(projDoc)
        .sort(sortDoc);
  }

  <ResultT> FindOneByIdOperation<ResultT> findOneById(
      final BsonValue documentId,
      final Class<ResultT> resultClass
  ) {
    notNull("documentId", documentId);
    return new FindOneByIdOperation<>(
        namespace,
        documentId,
        resultClass,
        dataSynchronizer);
  }

  UpdateOneByIdOperation<DocumentT> updateOneById(
      final BsonValue documentId,
      final Bson update
  ) {
    return new UpdateOneByIdOperation<>(
        namespace,
        documentId,
        toBsonDocument(update, documentClass, codecRegistry),
        dataSynchronizer);
  }

  public InsertOneAndSyncOperation insertOneAndSync(
      final DocumentT document
  ) {
    notNull("document", document);
    final DocumentT docToInsert;
    if (getCodec(codecRegistry, documentClass) instanceof CollectibleCodec) {
      docToInsert =
          ((CollectibleCodec<DocumentT>) getCodec(codecRegistry, documentClass))
              .generateIdIfAbsentFromDocument(document);
    } else {
      docToInsert = document;
    }
    return new InsertOneAndSyncOperation<>(
        namespace,
        documentToBsonDocument(docToInsert, codecRegistry),
        dataSynchronizer);
  }

  DeleteOneByIdOperation deleteOneById(final BsonValue documentId) {
    return new DeleteOneByIdOperation(
        namespace,
        documentId,
        dataSynchronizer);
  }
}
