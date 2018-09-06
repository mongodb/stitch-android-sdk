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

package com.mongodb.stitch.core.services.mongodb.remote.internal;

import static com.mongodb.stitch.core.internal.common.Assertions.notNull;
import static com.mongodb.stitch.core.internal.common.BsonUtils.documentToBsonDocument;
import static com.mongodb.stitch.core.internal.common.BsonUtils.getCodec;
import static com.mongodb.stitch.core.internal.common.BsonUtils.toBsonDocument;

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.internal.common.BsonUtils;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteCountOptions;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteFindOptions;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateOptions;
import java.util.ArrayList;
import java.util.List;
import org.bson.BsonDocument;
import org.bson.codecs.CollectibleCodec;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

public class Operations<DocumentT> {
  private final MongoNamespace namespace;
  private final Class<DocumentT> documentClass;
  private final CodecRegistry codecRegistry;

  protected Operations(
      final MongoNamespace namespace,
      final Class<DocumentT> documentClass,
      final CodecRegistry codecRegistry
  ) {
    this.namespace = namespace;
    this.documentClass = documentClass;
    this.codecRegistry = codecRegistry;
  }

  CountOperation count(final Bson filter, final RemoteCountOptions options) {
    return new CountOperation(namespace)
        .filter(toBsonDocument(filter, documentClass, codecRegistry))
        .limit(options.getLimit());
  }

  <ResultT> FindOperation<ResultT> findFirst(
      final Bson filter,
      final Class<ResultT> resultClass,
      final RemoteFindOptions options
  ) {
    return createFindOperation(namespace, filter, resultClass, options).limit(1);
  }

  <ResultT> FindOperation<ResultT> find(
      final Bson filter,
      final Class<ResultT> resultClass,
      final RemoteFindOptions options
  ) {
    return createFindOperation(namespace, filter, resultClass, options);
  }

  protected <ResultT> FindOperation<ResultT> createFindOperation(
      final MongoNamespace findNamespace,
      final Bson filter,
      final Class<ResultT> resultClass,
      final RemoteFindOptions options
  ) {
    return new FindOperation<>(findNamespace, codecRegistry.get(resultClass))
        .filter(filter.toBsonDocument(documentClass, codecRegistry))
        .limit(options.getLimit())
        .projection(BsonUtils.toBsonDocumentOrNull(
            options.getProjection(),
            documentClass,
            codecRegistry))
        .sort(BsonUtils.toBsonDocumentOrNull(
            options.getSort(),
            documentClass,
            codecRegistry));
  }

  <ResultT> AggregateOperation<ResultT> aggregate(
      final List<? extends Bson> pipeline,
      final Class<ResultT> resultClass
  ) {
    return new AggregateOperation<>(
        namespace, toBsonDocumentList(pipeline), codecRegistry.get(resultClass));
  }

  InsertOneOperation insertOne(final DocumentT document) {
    notNull("document", document);
    final DocumentT docToInsert;
    if (getCodec(codecRegistry, documentClass) instanceof CollectibleCodec) {
      docToInsert = ((CollectibleCodec<DocumentT>) getCodec(codecRegistry, documentClass))
          .generateIdIfAbsentFromDocument(document);
    } else {
      docToInsert = document;
    }
    return new InsertOneOperation(namespace, documentToBsonDocument(docToInsert, codecRegistry));
  }

  InsertManyOperation insertMany(
      final List<? extends DocumentT> documents
  ) {
    notNull("documents", documents);
    final List<BsonDocument> docs = new ArrayList<>(documents.size());
    for (final DocumentT document : documents) {
      if (document == null) {
        throw new IllegalArgumentException("documents can not contain a null value");
      }
      final DocumentT docToAdd;
      if (getCodec(codecRegistry, documentClass) instanceof CollectibleCodec) {
        docToAdd =
            ((CollectibleCodec<DocumentT>) getCodec(codecRegistry, documentClass))
                .generateIdIfAbsentFromDocument(document);
      } else {
        docToAdd = document;
      }
      docs.add(documentToBsonDocument(docToAdd, codecRegistry));
    }
    return new InsertManyOperation(namespace, docs);
  }

  DeleteOneOperation deleteOne(final Bson filter) {
    return new DeleteOneOperation(namespace, toBsonDocument(filter, documentClass, codecRegistry));
  }

  DeleteManyOperation deleteMany(final Bson filter) {
    return new DeleteManyOperation(namespace, toBsonDocument(filter, documentClass, codecRegistry));
  }

  UpdateOneOperation updateOne(
      final Bson filter,
      final Bson update,
      final RemoteUpdateOptions updateOptions
  ) {
    return new UpdateOneOperation(
        namespace,
        toBsonDocument(filter, documentClass, codecRegistry),
        toBsonDocument(update, documentClass, codecRegistry))
        .upsert(updateOptions.isUpsert());
  }

  UpdateManyOperation updateMany(
      final Bson filter,
      final Bson update,
      final RemoteUpdateOptions updateOptions
  ) {
    return new UpdateManyOperation(
        namespace,
        toBsonDocument(filter, documentClass, codecRegistry),
        toBsonDocument(update, documentClass, codecRegistry))
        .upsert(updateOptions.isUpsert());
  }

  private List<BsonDocument> toBsonDocumentList(final List<? extends Bson> bsonList) {
    if (bsonList == null) {
      return null;
    }
    final List<BsonDocument> bsonDocumentList = new ArrayList<>(bsonList.size());
    for (final Bson cur : bsonList) {
      bsonDocumentList.add(toBsonDocument(cur, documentClass, codecRegistry));
    }
    return bsonDocumentList;
  }
}
