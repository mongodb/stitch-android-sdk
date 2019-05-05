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

package com.mongodb.stitch.core.services.mongodb.remote.sync;

import com.mongodb.stitch.core.services.mongodb.remote.RemoteFindOptions;
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.SyncConfiguration;

import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import org.bson.BsonValue;
import org.bson.conversions.Bson;

/**
 * A set of synchronization related operations for a collection.
 *
 * @param <DocumentT> the type of document we are syncing on
 */
public interface CoreSync<DocumentT> {
  /**
   * Set the conflict resolver and and change event listener on this collection.
   * @param syncConfig the SyncConfiguration that contains relevant options
   */
  void configure(@Nonnull final SyncConfiguration syncConfig);

  /**
   * Requests that the given document _id be synchronized.
   * @param id the document _id to synchronize.
   */
  void syncOne(final BsonValue id);

  /**
   * Requests that the given document _ids be synchronized.
   * @param ids the document _ids to synchronize.
   */
  void syncMany(final BsonValue... ids);

  /**
   * Stops synchronizing the given document _id. Any uncommitted writes will be lost.
   *
   * @param id the _id of the document to desynchronize.
   */
  void desyncOne(final BsonValue id);

  /**
   * Stops synchronizing the given document _ids. Any uncommitted writes will be lost.
   *
   * @param ids the _ids of the documents to desynchronize.
   */
  void desyncMany(final BsonValue... ids);

  /**
   * Returns the set of synchronized document ids in a namespace.
   *
   * @return the set of synchronized document ids in a namespace.
   */
  Set<BsonValue> getSyncedIds();

  /**
   * Return the set of synchronized document _ids in a namespace
   * that have been paused due to an irrecoverable error.
   *
   * @return the set of paused document _ids in a namespace
   */
  Set<BsonValue> getPausedDocumentIds();

  /**
   * A document that is paused no longer has remote updates applied to it.
   * Any local updates to this document cause it to be resumed. An example of pausing a document
   * is when a conflict is being resolved for that document and the handler throws an exception.
   *
   * @param documentId the id of the document to resume syncing
   * @return true if successfully resumed, false if the document
   *         could not be found or there was an error resuming
   */
  boolean resumeSyncForDocument(final BsonValue documentId);

  /**
   * Counts the number of documents in the collection that have been synchronized with the remote.
   *
   * @return the number of documents in the collection
   */
  long count();

  /**
   * Counts the number of documents in the collection that have been synchronized with the remote
   * according to the given options.
   *
   * @param filter the query filter
   * @return the number of documents in the collection
   */
  long count(final Bson filter);

  /**
   * Counts the number of documents in the collection that have been synchronized with the remote
   * according to the given options.
   *
   * @param filter  the query filter
   * @param options the options describing the count
   * @return the number of documents in the collection
   */
  long count(final Bson filter, final SyncCountOptions options);

  /**
   * Finds a document in the collection.
   *
   * @return the resulting document
   */
  DocumentT findOne();

  /**
   * Finds a document in the collection.
   *
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type
   * @return the resulting document
   */
  <ResultT> ResultT findOne(final Class<ResultT> resultClass);

  /**
   * Finds a document in the collection.
   *
   * @param filter the query filter
   * @return the resulting document
   */
  DocumentT findOne(final Bson filter);

  /**
   * Finds a document in the collection.
   *
   * @param filter      the query filter
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return the resulting document
   */
  <ResultT> ResultT findOne(final Bson filter, final Class<ResultT> resultClass);

  /**
   * Finds a document in the collection.
   *
   * @param filter the query filter
   * @param options A RemoteFindOptions struct
   * @return the resulting document
   */
  DocumentT findOne(final Bson filter, final RemoteFindOptions options);

  /**
   * Finds a document in the collection.
   *
   * @param filter      the query filter
   * @param options     A RemoteFindOptions struct
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return the resulting document
   */
  <ResultT> ResultT findOne(
          final Bson filter,
          final RemoteFindOptions options,
          final Class<ResultT> resultClass);

  /**
   * Finds all documents in the collection that have been synchronized with the remote.
   *
   * @return the find iterable interface
   */
  CoreSyncFindIterable<DocumentT> find();

  /**
   * Finds all documents in the collection that have been synchronized with the remote.
   *
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return the find iterable interface
   */
  <ResultT> CoreSyncFindIterable<ResultT> find(final Class<ResultT> resultClass);

  /**
   * Finds all documents in the collection that have been synchronized with the remote.
   *
   * @param filter the query filter
   * @return the find iterable interface
   */
  CoreSyncFindIterable<DocumentT> find(final Bson filter);

  /**
   * Finds all documents in the collection that have been synchronized with the remote.
   *
   * @param filter      the query filter
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return the find iterable interface
   */
  <ResultT> CoreSyncFindIterable<ResultT> find(
      final Bson filter,
      final Class<ResultT> resultClass);


  /**
   * Aggregates documents that have been synchronized with the remote
   * according to the specified aggregation pipeline.
   *
   * @param pipeline the aggregation pipeline
   * @return an iterable containing the result of the aggregation operation
   */
  CoreSyncAggregateIterable<DocumentT> aggregate(final List<? extends Bson> pipeline);

  /**
   * Aggregates documents that have been synchronized with the remote
   * according to the specified aggregation pipeline.
   *
   * @param pipeline    the aggregation pipeline
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return an iterable containing the result of the aggregation operation
   */
  <ResultT> CoreSyncAggregateIterable<ResultT> aggregate(
      final List<? extends Bson> pipeline,
      final Class<ResultT> resultClass);

  /**
   * Inserts the provided document. If the document is missing an identifier, the client should
   * generate one. Syncs the newly inserted document against the remote.
   *
   * @param document the document to insert
   * @return the result of the insert one operation
   */
  SyncInsertOneResult insertOne(final DocumentT document);

  /**
   * Inserts one or more documents. Syncs the newly inserted documents against the remote.
   *
   * @param documents the documents to insert
   * @return the result of the insert many operation
   */
  SyncInsertManyResult insertMany(final List<DocumentT> documents);

  /**
   * Removes at most one document from the collection that has been synchronized with the remote
   * that matches the given filter.  If no documents match, the collection is not
   * modified.
   *
   * @param filter the query filter to apply the the delete operation
   * @return the result of the remove one operation
   */
  SyncDeleteResult deleteOne(final Bson filter);

  /**
   * Removes all documents from the collection that have been synchronized with the remote
   * that match the given query filter.  If no documents match, the collection is not modified.
   *
   * @param filter the query filter to apply the the delete operation
   * @return the result of the remove many operation
   */
  SyncDeleteResult deleteMany(final Bson filter);

  /**
   * Update a single document in the collection that have been synchronized with the remote
   * according to the specified arguments. If the update results in an upsert,
   * the newly upserted document will automatically become synchronized.
   *
   * @param filter a document describing the query filter, which may not be null.
   * @param update a document describing the update, which may not be null. The update to
   *               apply must include only update operators.
   * @return the result of the update one operation
   */
  SyncUpdateResult updateOne(final Bson filter, final Bson update);

  /**
   * Update a single document in the collection that has been synchronized with the remote
   * according to the specified arguments. If the update results in an upsert,
   * the newly upserted document will automatically become synchronized.
   *
   * @param filter        a document describing the query filter, which may not be null.
   * @param update        a document describing the update, which may not be null. The update to
   *                      apply must include only update operators.
   * @param updateOptions the options to apply to the update operation
   * @return the result of the update one operation
   */
  SyncUpdateResult updateOne(
      final Bson filter,
      final Bson update,
      final SyncUpdateOptions updateOptions);

  /**
   * Update all documents in the collection that have been synchronized with the remote
   * according to the specified arguments. If the update results in an upsert,
   * the newly upserted document will automatically become synchronized.
   *
   * @param filter a document describing the query filter, which may not be null.
   * @param update a document describing the update, which may not be null. The update to
   *               apply must include only update operators.
   * @return the result of the update many operation
   */
  SyncUpdateResult updateMany(final Bson filter, final Bson update);

  /**
   * Update all documents in the collection that have been synchronized with the remote
   * according to the specified arguments. If the update results in an upsert,
   * the newly upserted document will automatically become synchronized.
   *
   * @param filter        a document describing the query filter, which may not be null.
   * @param update        a document describing the update, which may not be null. The update to
   *                     apply must include only update operators.
   * @param updateOptions the options to apply to the update operation
   * @return the result of the update many operation
   */
  SyncUpdateResult updateMany(
      final Bson filter,
      final Bson update,
      final SyncUpdateOptions updateOptions);
}
