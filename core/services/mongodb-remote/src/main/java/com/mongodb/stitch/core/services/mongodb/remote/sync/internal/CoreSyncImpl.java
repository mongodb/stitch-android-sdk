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
import com.mongodb.client.model.WriteModel;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler;
import com.mongodb.stitch.core.services.mongodb.remote.sync.CoreSync;
import com.mongodb.stitch.core.services.mongodb.remote.sync.CoreSyncAggregateIterable;
import com.mongodb.stitch.core.services.mongodb.remote.sync.CoreSyncFindIterable;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncCountOptions;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncDeleteResult;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncInsertManyResult;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncInsertOneResult;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncUpdateOptions;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncUpdateResult;

import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.conversions.Bson;

public class CoreSyncImpl<DocumentT> implements CoreSync<DocumentT> {
  private final DataSynchronizer dataSynchronizer;
  private final SyncOperations<DocumentT> syncOperations;
  private final MongoNamespace namespace;
  private final Class<DocumentT> documentClass;
  private final CoreStitchServiceClient service;

  public CoreSyncImpl(final MongoNamespace namespace,
                      final Class<DocumentT> documentClass,
                      final DataSynchronizer dataSynchronizer,
                      final CoreStitchServiceClient service,
                      final SyncOperations<DocumentT> syncOperations) {
    this.namespace = namespace;
    this.documentClass = documentClass;
    this.dataSynchronizer = dataSynchronizer;
    this.syncOperations = syncOperations;
    this.service = service;
  }

  @Override
  public void configure(@Nonnull final ConflictHandler<DocumentT> conflictHandler,
                        @Nullable final ChangeEventListener<DocumentT> changeEventListener,
                        @Nullable final ExceptionListener exceptionListener) {
    this.dataSynchronizer.configure(
        namespace,
        conflictHandler,
        changeEventListener,
        exceptionListener,
        this.service.getCodecRegistry().get(documentClass)
    );
  }

  @Override
  public void syncOne(final BsonValue id) {
    this.dataSynchronizer.syncDocumentsFromRemote(this.namespace, id);
  }

  @Override
  public void syncMany(final BsonValue... ids) {
    this.dataSynchronizer.syncDocumentsFromRemote(this.namespace, ids);
  }

  @Override
  public void desyncOne(final BsonValue id) {
    this.dataSynchronizer.getLocalCollection(namespace).bulkWrite(
        this.dataSynchronizer.desyncDocumentsFromRemote(namespace, id).bulkWriteModels
    );
  }

  @Override
  public void desyncMany(final BsonValue... ids) {
    this.dataSynchronizer.getLocalCollection(namespace).bulkWrite(
        this.dataSynchronizer.desyncDocumentsFromRemote(namespace, ids).bulkWriteModels
    );
  }

  @Override
  public Set<BsonValue> getSyncedIds() {
    return this.dataSynchronizer.getSynchronizedDocumentIds(namespace);
  }

  @Override
  public Set<BsonValue> getPausedDocumentIds() {
    return this.dataSynchronizer.getPausedDocumentIds(namespace);
  }

  @Override
  public boolean resumeSyncForDocument(final BsonValue documentId) {
    return this.dataSynchronizer.resumeSyncForDocument(namespace, documentId);
  }

  @Override
  public long count() {
    return count(new BsonDocument());
  }

  @Override
  public long count(final Bson filter) {
    return count(filter, new SyncCountOptions());
  }

  @Override
  public long count(final Bson filter, final SyncCountOptions options) {
    return syncOperations.count(filter, options).execute(service);
  }

  @Override
  public CoreSyncAggregateIterable<DocumentT> aggregate(final List<? extends Bson> pipeline) {
    return this.aggregate(pipeline, this.documentClass);
  }

  @Override
  public <ResultT> CoreSyncAggregateIterable<ResultT> aggregate(
      final List<? extends Bson> pipeline,
      final Class<ResultT> resultClass
  ) {
    return new CoreSyncAggregateIterableImpl<>(
        pipeline, resultClass, service, syncOperations
    );
  }

  @Override
  public SyncUpdateResult updateOne(final Bson filter, final Bson update) {
    return this.updateOne(filter, update, new SyncUpdateOptions());
  }

  @Override
  public SyncUpdateResult updateOne(
      final Bson filter,
      final Bson update,
      final SyncUpdateOptions updateOptions
  ) {
    return syncOperations.updateOne(filter, update, updateOptions).execute(service);
  }

  @Override
  public SyncUpdateResult updateMany(final Bson filter, final Bson update) {
    return this.updateMany(filter, update, new SyncUpdateOptions());
  }

  @Override
  public SyncUpdateResult updateMany(
      final Bson filter,
      final Bson update,
      final SyncUpdateOptions updateOptions
  ) {
    return this.syncOperations.updateMany(filter, update, updateOptions).execute(service);
  }

  @Override
  public SyncInsertOneResult insertOne(final DocumentT document) {
    return syncOperations.insertOne(document).execute(service);
  }

  @Override
  public SyncInsertManyResult insertMany(final List<DocumentT> documents) {
    return syncOperations.insertMany(documents).execute(service);
  }

  @Override
  public SyncDeleteResult deleteOne(final Bson filter) {
    return syncOperations.deleteOne(filter).execute(service);
  }

  @Override
  public SyncDeleteResult deleteMany(final Bson filter) {
    return syncOperations.deleteMany(filter).execute(service);
  }

  @Override
  public CoreSyncFindIterable<DocumentT> find() {
    return this.find(new BsonDocument(), documentClass);
  }

  @Override
  public CoreSyncFindIterable<DocumentT> find(final Bson filter) {
    return this.find(filter, documentClass);
  }

  @Override
  public <ResultT> CoreSyncFindIterable<ResultT> find(final Class<ResultT> resultClass) {
    return this.find(new BsonDocument(), resultClass);
  }

  /**
   * Finds all documents in the collection.
   *
   * @param filter      the query filter
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return the find iterable interface
   */
  public <ResultT> CoreSyncFindIterable<ResultT> find(final Bson filter,
                                                      final Class<ResultT> resultClass) {
    return createFindIterable(filter, resultClass);
  }

  private <ResultT> CoreSyncFindIterable<ResultT> createFindIterable(
      final Bson filter,
      final Class<ResultT> resultClass) {
    return new CoreSyncFindIterableImpl<>(
            filter,
            resultClass,
            service,
            syncOperations);
  }
}
