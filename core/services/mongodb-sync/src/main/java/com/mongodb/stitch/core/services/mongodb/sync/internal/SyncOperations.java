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

import static com.mongodb.stitch.core.internal.common.Assertions.notNull;
import static com.mongodb.stitch.core.internal.common.BsonUtils.documentToBsonDocument;
import static com.mongodb.stitch.core.internal.common.BsonUtils.getCodec;
import static com.mongodb.stitch.core.internal.common.BsonUtils.toBsonDocument;

import com.mongodb.MongoNamespace;
import com.mongodb.client.MongoDatabase;
import com.mongodb.stitch.core.internal.common.BsonUtils;
import com.mongodb.stitch.core.internal.net.NetworkMonitor;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteFindOptions;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoCollection;
import com.mongodb.stitch.core.services.mongodb.remote.internal.Operations;
import com.mongodb.stitch.core.services.mongodb.sync.ChangeEventListener;
import com.mongodb.stitch.core.services.mongodb.sync.SyncConflictResolver;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.codecs.CollectibleCodec;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

class SyncOperations<DocumentT> extends Operations<DocumentT> {

  private final MongoNamespace namespace;
  private final Class<DocumentT> documentClass;
  private final DataSynchronizer dataSynchronizer;
  private final NetworkMonitor networkMonitor;
  private final CodecRegistry codecRegistry;
  private final CoreRemoteMongoCollection<DocumentT> remoteCollection;
  private final MongoDatabase tempDb;

  SyncOperations(
      final MongoNamespace namespace,
      final Class<DocumentT> documentClass,
      final DataSynchronizer dataSynchronizer,
      final NetworkMonitor networkMonitor,
      final CodecRegistry codecRegistry,
      final CoreRemoteMongoCollection<DocumentT> remoteCollection,
      final MongoDatabase tempDb
  ) {
    super(namespace, documentClass, codecRegistry);
    this.namespace = namespace;
    this.documentClass = documentClass;
    this.dataSynchronizer = dataSynchronizer;
    this.codecRegistry = codecRegistry;
    this.networkMonitor = networkMonitor;
    this.remoteCollection = remoteCollection;
    this.tempDb = tempDb;
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
        super.createFindOperation(namespace, filterDoc, BsonDocument.class, options)
            .limit(options.getLimit())
            .sort(sortDoc),
        resultClass,
        dataSynchronizer,
        networkMonitor,
        tempDb.getCollection(new ObjectId().toHexString(), BsonDocument.class))
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
        dataSynchronizer,
        networkMonitor,
        remoteCollection.withDocumentClass(resultClass));
  }

  UpdateOneByIdOperation<DocumentT> updateOneById(
      final BsonValue documentId,
      final Bson update
  ) {
    return new UpdateOneByIdOperation<>(
        namespace,
        documentId,
        toBsonDocument(update, documentClass, codecRegistry),
        dataSynchronizer,
        networkMonitor,
        remoteCollection);
  }

  public InsertOneAndSyncOperation insertOneAndSync(
      final DocumentT document,
      final SyncConflictResolver<DocumentT> conflictResolver
  ) {
    return insertOneAndSync(document, conflictResolver, null);
  }

  public InsertOneAndSyncOperation insertOneAndSync(
      final DocumentT document,
      final SyncConflictResolver<DocumentT> conflictResolver,
      final ChangeEventListener<DocumentT> eventListener
  ) {
    notNull("document", document);
    notNull("conflictResolver", conflictResolver);
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
        dataSynchronizer,
        conflictResolver,
        eventListener,
        codecRegistry.get(documentClass));
  }

  DeleteOneByIdOperation deleteOneById(final BsonValue documentId) {
    return new DeleteOneByIdOperation(
        namespace,
        documentId,
        dataSynchronizer,
        networkMonitor,
        remoteCollection);
  }
}
