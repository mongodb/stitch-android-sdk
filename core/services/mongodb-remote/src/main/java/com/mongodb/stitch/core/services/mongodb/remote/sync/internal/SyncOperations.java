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
import com.mongodb.client.model.CountOptions;
import com.mongodb.stitch.core.internal.common.BsonUtils;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteFindOptions;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncCountOptions;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncUpdateOptions;

import java.util.ArrayList;
import java.util.List;

import org.bson.BsonDocument;
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

  <ResultT> FindOneOperation<ResultT> findOne(
      final Bson filter,
      final RemoteFindOptions options,
      final Class<ResultT> resultClass
  ) {
    BsonDocument projection = null;
    BsonDocument sort = null;
    if (options != null) {
      projection = BsonUtils.toBsonDocumentOrNull(
              options.getProjection(),
              documentClass,
              codecRegistry);

      sort =  BsonUtils.toBsonDocumentOrNull(options.getSort(), documentClass, codecRegistry);
    }

    return new FindOneOperation<>(
            namespace,
            resultClass,
            filter.toBsonDocument(documentClass, codecRegistry),
            projection,
            sort,
            dataSynchronizer);

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

  CountOperation count(final Bson filter, final SyncCountOptions countOptions) {
    return new CountOperation(
        namespace,
        dataSynchronizer,
        filter,
        new CountOptions().limit(countOptions.getLimit()));
  }

  /**
   * Aggregates documents according to the specified aggregation pipeline.
   *
   * @param pipeline    the aggregation pipeline
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return an iterable containing the result of the aggregation operation
   */
  <ResultT> AggregateOperation<ResultT> aggregate(
      final List<? extends Bson> pipeline,
      final Class<ResultT> resultClass) {
    return new AggregateOperation<>(namespace, dataSynchronizer, pipeline, resultClass);
  }

  UpdateOneOperation updateOne(
      final Bson filter,
      final Bson update,
      final SyncUpdateOptions updateOptions
  ) {
    return new UpdateOneOperation(
        namespace,
        filter,
        toBsonDocument(update, documentClass, codecRegistry),
        dataSynchronizer,
        updateOptions);
  }

  UpdateManyOperation updateMany(
      final Bson filter,
      final Bson update,
      final SyncUpdateOptions updateOptions
  ) {
    return new UpdateManyOperation(
        namespace,
        filter,
        toBsonDocument(update, documentClass, codecRegistry),
        dataSynchronizer,
        updateOptions);
  }

  public InsertOneOperation insertOne(final DocumentT document) {
    notNull("document", document);
    final DocumentT docToInsert;
    if (getCodec(codecRegistry, documentClass) instanceof CollectibleCodec) {
      docToInsert =
          ((CollectibleCodec<DocumentT>) getCodec(codecRegistry, documentClass))
              .generateIdIfAbsentFromDocument(document);
    } else {
      docToInsert = document;
    }

    return new InsertOneOperation(
        namespace,
        documentToBsonDocument(docToInsert, codecRegistry),
        dataSynchronizer);
  }

  InsertManyOperation insertMany(final List<DocumentT> documents) {
    final List<BsonDocument> bsonDocuments = new ArrayList<>();
    for (final DocumentT document : documents) {
      if (getCodec(codecRegistry, documentClass) instanceof CollectibleCodec) {
        bsonDocuments.add(
            documentToBsonDocument(
                ((CollectibleCodec<DocumentT>) getCodec(codecRegistry, documentClass))
                    .generateIdIfAbsentFromDocument(document),
                codecRegistry
            )
        );
      } else {
        bsonDocuments.add(documentToBsonDocument(document, codecRegistry));
      }
    }

    return new InsertManyOperation(
        namespace,
        bsonDocuments,
        dataSynchronizer);
  }

  DeleteOneOperation deleteOne(final Bson filter) {
    return new DeleteOneOperation(
        namespace,
        filter,
        dataSynchronizer);
  }

  DeleteManyOperation deleteMany(final Bson filter) {
    return new DeleteManyOperation(
        namespace,
        filter,
        dataSynchronizer);
  }
}
