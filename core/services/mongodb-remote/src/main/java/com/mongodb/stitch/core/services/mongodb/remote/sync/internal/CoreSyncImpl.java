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
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertOneResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler;
import com.mongodb.stitch.core.services.mongodb.remote.sync.CoreSync;
import com.mongodb.stitch.core.services.mongodb.remote.sync.CoreSyncFindIterable;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ErrorListener;

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
                        final ChangeEventListener<DocumentT> changeEventListener,
                        final ErrorListener errorListener) {
    this.dataSynchronizer.configure(
        namespace,
        conflictHandler,
        changeEventListener,
        errorListener,
        this.service.getCodecRegistry().get(documentClass)
    );
  }

  @Override
  public void syncOne(final BsonValue id) {
    this.dataSynchronizer.syncDocumentFromRemote(this.namespace, id);
  }

  @Override
  public void syncMany(final BsonValue... ids) {
    for (final BsonValue id: ids) {
      this.syncOne(id);
    }
  }

  @Override
  public void desyncOne(final BsonValue id) {
    this.dataSynchronizer.desyncDocumentFromRemote(namespace, id);
  }

  @Override
  public void desyncMany(final BsonValue... ids) {
    for (final BsonValue id: ids) {
      this.desyncOne(id);
    }
  }

  @Override
  public Set<BsonValue> getSyncedIds() {
    return this.dataSynchronizer.getSynchronizedDocumentIds(namespace);
  }

  /**
   * Finds a single document by the given id. It is first searched for in the local synchronized
   * cache and if not found and there is internet connectivity, it is searched for remotely.
   *
   * @param documentId the _id of the document to search for.
   * @return the document if found locally or remotely.
   */
  @Nullable
  public DocumentT findOneById(final BsonValue documentId) {
    return findOneById(documentId, this.documentClass);
  }

  /**
   * Finds a single document by the given id. It is first searched for in the local synchronized
   * cache and if not found and there is internet connectivity, it is searched for remotely.
   *
   * @param documentId the _id of the document to search for.
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return the document if found locally or remotely.
   */
  @Nullable
  public <ResultT> ResultT findOneById(final BsonValue documentId,
                                       final Class<ResultT> resultClass) {
    return syncOperations.findOneById(documentId, resultClass).execute(service);
  }

  /**
   * Updates a document by the given id. It is first searched for in the local synchronized cache
   * and if not found and there is internet connectivity, it is searched for remotely.
   *
   * @param documentId the _id of the document to search for.
   * @param update the update specifier.
   * @return the result of the local or remote update.
   */
  public RemoteUpdateResult updateOneById(final BsonValue documentId, final Bson update) {
    return syncOperations.updateOneById(documentId, update).execute(service);
  }

  /**
   * Inserts a single document and begins to synchronize it.
   *
   * @param document the document to insert and synchronize.
   * @return the result of the insertion.
   */
  public RemoteInsertOneResult insertOneAndSync(final DocumentT document) {
    return syncOperations.insertOneAndSync(document).execute(service);
  }

  /**
   * Deletes a single document by the given id. It is first searched for in the local synchronized
   * cache and if not found and there is internet connectivity, it is searched for remotely.
   *
   * @param documentId the _id of the document to search for.
   * @return the result of the local or remote update.
   */
  public RemoteDeleteResult deleteOneById(final BsonValue documentId) {
    return syncOperations.deleteOneById(documentId).execute(service);
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
