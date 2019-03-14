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

package com.mongodb.stitch.android.services.mongodb.remote;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncCountOptions;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncDeleteResult;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncInsertManyResult;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncInsertOneResult;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncUpdateOptions;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncUpdateResult;

import java.util.List;
import java.util.Set;

import org.bson.BsonValue;
import org.bson.conversions.Bson;

/**
 * A set of synchronization related operations for a collection.
 *
 * @param <DocumentT> the type of document we are syncing on
 */
public interface Sync<DocumentT> {
  /**
   * Set the conflict handler and and change event listener on this collection. This will start
   * a background sync thread, and should be called before any CRUD operations are attempted.
   *
   * @param conflictHandler the conflict resolver to invoke when a conflict happens between local
   *                         and remote events.
   * @param changeEventListener the event listener to invoke when a change event happens for the
   *                         document.
   * @param exceptionListener the error listener to invoke when an irrecoverable error occurs
   *
   * @return A Task that completes when Mobile Sync is configured, and the background sync thread
   *         has started.
   */
  Task<Void> configure(@NonNull final ConflictHandler<DocumentT> conflictHandler,
                       @Nullable final ChangeEventListener<DocumentT> changeEventListener,
                       @Nullable final ExceptionListener exceptionListener);

  /**
   * Requests that the given document _id be synchronized.
   * @param id the document _id to synchronize.
   *
   * @return a Task that completes when the specified document ID is configured as synced. The
   *         actual document may not be in sync with the remote collection until the next sync
   *         pass.
   */
  Task<Void> syncOne(final BsonValue id);

  /**
   * Requests that the given document _ids be synchronized.
   * @param ids the document _ids to synchronize.
   *
   * @return a Task that completes when the specified document IDs are configured as synced. The
   *         actual documents may not be in sync with the remote collection until the next sync
   *         pass.
   */
  Task<Void> syncMany(final BsonValue... ids);

  /**
   * Stops synchronizing the given document _id. Any uncommitted writes will be lost.
   *
   * @param id the _id of the document to desynchronize.
   *
   * @return a Task that completes when the specified document ID is no longer configured as
   *         synced. The document will be removed from the local collection, but it will not be
   *         necessarily deleted from the remote collection.
   */
  Task<Void> desyncOne(final BsonValue id);

  /**
   * Stops synchronizing the given document _ids. Any uncommitted writes will be lost.
   *
   * @param ids the _ids of the documents to desynchronize.
   *
   * @return a Task that completes when the specified document ID is no longer configured as
   *         synced. The documents will be removed from the local collection, but they are not
   *         necessarily deleted from the remote collection.
   */
  Task<Void> desyncMany(final BsonValue... ids);

  /**
   * Returns the set of synchronized document ids in a namespace.
   *
   * @return the set of synchronized document ids in a namespace.
   */
  Task<Set<BsonValue>> getSyncedIds();

  /**
   * Return the set of synchronized document _ids in a namespace
   * that have been paused due to an irrecoverable error.
   *
   * @return the set of paused document _ids in a namespace
   */
  Task<Set<BsonValue>> getPausedDocumentIds();

  /**
   * A document that is paused no longer has remote updates applied to it.
   * Any local updates to this document cause it to be resumed. An example of pausing a document
   * is when a conflict is being resolved for that document and the handler throws an exception.
   *
   * @param documentId the id of the document to resume syncing
   * @return true if successfully resumed, false if the document
   *         could not be found or there was an error resuming
   */
  Task<Boolean> resumeSyncForDocument(@NonNull final BsonValue documentId);

  /**
   * Counts the number of documents in the collection that have been synchronized with the remote.
   *
   * @return the number of documents in the collection
   */
  Task<Long> count();

  /**
   * Counts the number of documents in the collection that have been synchronized with the remote
   * according to the given options.
   *
   * @param filter the query filter
   * @return the number of documents in the collection
   */
  Task<Long> count(final Bson filter);

  /**
   * Counts the number of documents in the collection that have been synchronized with the remote
   * according to the given options.
   *
   * @param filter  the query filter
   * @param options the options describing the count
   * @return the number of documents in the collection
   */
  Task<Long> count(final Bson filter, final SyncCountOptions options);

  /**
   * Finds all documents in the collection that have been synchronized with the remote.
   *
   * @return the find iterable interface
   */
  SyncFindIterable<DocumentT> find();

  /**
   * Finds all documents in the collection that have been synchronized with the remote.
   *
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return the find iterable interface
   */
  <ResultT> SyncFindIterable<ResultT> find(final Class<ResultT> resultClass);

  /**
   * Finds all documents in the collection that have been synchronized with the remote.
   *
   * @param filter the query filter
   * @return the find iterable interface
   */
  SyncFindIterable<DocumentT> find(final Bson filter);

  /**
   * Finds all documents in the collection that have been synchronized with the remote.
   *
   * @param filter      the query filter
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return the find iterable interface
   */
  <ResultT> SyncFindIterable<ResultT> find(
      final Bson filter,
      final Class<ResultT> resultClass);


  /**
   * Aggregates documents that have been synchronized with the remote
   * according to the specified aggregation pipeline.
   *
   * @param pipeline the aggregation pipeline
   * @return an iterable containing the result of the aggregation operation
   */
  SyncAggregateIterable<DocumentT> aggregate(final List<? extends Bson> pipeline);

  /**
   * Aggregates documents that have been synchronized with the remote
   * according to the specified aggregation pipeline.
   *
   * @param pipeline    the aggregation pipeline
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return an iterable containing the result of the aggregation operation
   */
  <ResultT> SyncAggregateIterable<ResultT> aggregate(
      final List<? extends Bson> pipeline,
      final Class<ResultT> resultClass);

  /**
   * Inserts the provided document. If the document is missing an identifier, one will be
   * generated. Begin synchronizating on the document's id.
   *
   * @param document the document to insert
   * @return the result of the insert one operation
   */
  Task<SyncInsertOneResult> insertOne(final DocumentT document);

  /**
   * Inserts one or more documents. If the documents are missing an identifier, they will be
   * generated. Begin synchronizing on the documents' ids.
   *
   * @param documents the documents to insert
   * @return the result of the insert many operation
   */
  Task<SyncInsertManyResult> insertMany(final List<DocumentT> documents);

  /**
   * Removes at most one document that has been synchronized with the remote
   * from the collection that matches the given filter.  If no
   * documents match, the collection is not modified.
   *
   * @param filter the query filter to apply the the delete operation
   * @return the result of the remove one operation
   */
  Task<SyncDeleteResult> deleteOne(final Bson filter);

  /**
   * Removes all documents from the collection that have been synchronized with the remote
   * that match the given query filter.  If no documents match, the collection is not modified.
   *
   * @param filter the query filter to apply the the delete operation
   * @return the result of the remove many operation
   */
  Task<SyncDeleteResult> deleteMany(final Bson filter);

  /**
   * Update a single document that has been synchronized with the remote
   * in the collection according to the specified arguments. If the update results in an upsert,
   * the newly upserted document will automatically become synchronized.
   *
   * @param filter a document describing the query filter, which may not be null.
   * @param update a document describing the update, which may not be null. The update to
   *               apply must include only update operators.
   * @return the result of the update one operation
   */
  Task<SyncUpdateResult> updateOne(final Bson filter, final Bson update);

  /**
   * Update a single document that has been synchronized with the remote
   * in the collection according to the specified arguments. If the update results in an upsert,
   * the newly upserted document will automatically become synchronized.
   *
   * @param filter        a document describing the query filter, which may not be null.
   * @param update        a document describing the update, which may not be null. The update to
   *                      apply must include only update operators.
   * @param updateOptions the options to apply to the update operation
   * @return the result of the update one operation
   */
  Task<SyncUpdateResult> updateOne(
      final Bson filter,
      final Bson update,
      final SyncUpdateOptions updateOptions);

  /**
   * Update all documents that have been synchronized with the remote
   * in the collection according to the specified arguments. If the update results in an upsert,
   * the newly upserted document will automatically become synchronized.
   *
   * @param filter a document describing the query filter, which may not be null.
   * @param update a document describing the update, which may not be null. The update to
   *               apply must include only update operators.
   * @return the result of the update many operation
   */
  Task<SyncUpdateResult> updateMany(final Bson filter, final Bson update);

  /**
   * Update all documents that have been synchronized with the remote
   * in the collection according to the specified arguments. If the update results in an upsert,
   * the newly upserted document will automatically become synchronized.
   *
   * @param filter        a document describing the query filter, which may not be null.
   * @param update        a document describing the update, which may not be null. The update to
   *                     apply must include only update operators.
   * @param updateOptions the options to apply to the update operation
   * @return the result of the update many operation
   */
  Task<SyncUpdateResult> updateMany(
      final Bson filter,
      final Bson update,
      final SyncUpdateOptions updateOptions);
}
