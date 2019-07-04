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

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.internal.net.Stream;
import com.mongodb.stitch.core.services.mongodb.remote.ChangeEvent;
import com.mongodb.stitch.core.services.mongodb.remote.CompactChangeEvent;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteCountOptions;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteFindOneAndModifyOptions;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteFindOptions;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertManyResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertOneResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateOptions;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult;
import com.mongodb.stitch.core.services.mongodb.remote.sync.CoreSync;

import java.io.IOException;
import java.util.List;

import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.BsonValue;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

public interface CoreRemoteMongoCollection<DocumentT> {

  /**
   * Gets the namespace of this collection.
   *
   * @return the namespace
   */
  MongoNamespace getNamespace();

  /**
   * Get the class of documents stored in this collection.
   *
   * @return the class
   */
  Class<DocumentT> getDocumentClass();

  /**
   * Get the codec registry for the CoreRemoteMongoCollection.
   *
   * @return the {@link org.bson.codecs.configuration.CodecRegistry}
   */
  CodecRegistry getCodecRegistry();

  /**
   * Create a new CoreRemoteMongoCollection instance with a different default class to cast any
   * documents returned from the database into.
   *
   * @param clazz the default class to cast any documents returned from the database into.
   * @param <NewDocumentT> the type that the new collection will encode documents from and decode
   *                      documents to.
   * @return a new CoreRemoteMongoCollection instance with the different default class
   */
  <NewDocumentT> CoreRemoteMongoCollection<NewDocumentT> withDocumentClass(
      final Class<NewDocumentT> clazz);

  /**
   * Create a new CoreRemoteMongoCollection instance with a different codec registry.
   *
   * @param codecRegistry the new {@link org.bson.codecs.configuration.CodecRegistry} for the
   *                      collection.
   * @return a new CoreRemoteMongoCollection instance with the different codec registry
   */
  CoreRemoteMongoCollection<DocumentT> withCodecRegistry(final CodecRegistry codecRegistry);

  /**
   * Counts the number of documents in the collection.
   *
   * @return the number of documents in the collection
   */
  long count();

  /**
   * Counts the number of documents in the collection according to the given options.
   *
   * @param filter the query filter
   * @return the number of documents in the collection
   */
  long count(final Bson filter);

  /**
   * Counts the number of documents in the collection according to the given options.
   *
   * @param filter  the query filter
   * @param options the options describing the count
   * @return the number of documents in the collection
   */
  long count(final Bson filter, final RemoteCountOptions options);

  /**
   * Finds a document in the collection
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
   * Finds all documents in the collection.
   *
   * @return the find iterable interface
   */
  CoreRemoteFindIterable<DocumentT> find();

  /**
   * Finds all documents in the collection.
   *
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return the find iterable interface
   */
  <ResultT> CoreRemoteFindIterable<ResultT> find(final Class<ResultT> resultClass);

  /**
   * Finds all documents in the collection.
   *
   * @param filter the query filter
   * @return the find iterable interface
   */
  CoreRemoteFindIterable<DocumentT> find(final Bson filter);

  /**
   * Finds all documents in the collection.
   *
   * @param filter      the query filter
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return the find iterable interface
   */
  <ResultT> CoreRemoteFindIterable<ResultT> find(
      final Bson filter,
      final Class<ResultT> resultClass);


  /**
   * Aggregates documents according to the specified aggregation pipeline.
   *
   * @param pipeline the aggregation pipeline
   * @return an iterable containing the result of the aggregation operation
   */
  CoreRemoteAggregateIterable<DocumentT> aggregate(final List<? extends Bson> pipeline);

  /**
   * Aggregates documents according to the specified aggregation pipeline.
   *
   * @param pipeline    the aggregation pipeline
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return an iterable containing the result of the aggregation operation
   */
  <ResultT> CoreRemoteAggregateIterable<ResultT> aggregate(
      final List<? extends Bson> pipeline,
      final Class<ResultT> resultClass);

  /**
   * Inserts the provided document. If the document is missing an identifier, the client should
   * generate one.
   *
   * @param document the document to insert
   * @return the result of the insert one operation
   */
  RemoteInsertOneResult insertOne(final DocumentT document);

  /**
   * Inserts one or more documents.
   *
   * @param documents the documents to insert
   * @return the result of the insert many operation
   */
  RemoteInsertManyResult insertMany(final List<? extends DocumentT> documents);

  /**
   * Removes at most one document from the collection that matches the given filter.  If no
   * documents match, the collection is not
   * modified.
   *
   * @param filter the query filter to apply the the delete operation
   * @return the result of the remove one operation
   */
  RemoteDeleteResult deleteOne(final Bson filter);

  /**
   * Removes all documents from the collection that match the given query filter.  If no documents
   * match, the collection is not modified.
   *
   * @param filter the query filter to apply the the delete operation
   * @return the result of the remove many operation
   */
  RemoteDeleteResult deleteMany(final Bson filter);

  /**
   * Update a single document in the collection according to the specified arguments.
   *
   * @param filter a document describing the query filter, which may not be null.
   * @param update a document describing the update, which may not be null. The update to
   *               apply must include only update operators.
   * @return the result of the update one operation
   */
  RemoteUpdateResult updateOne(final Bson filter, final Bson update);

  /**
   * Update a single document in the collection according to the specified arguments.
   *
   * @param filter        a document describing the query filter, which may not be null.
   * @param update        a document describing the update, which may not be null. The update to
   *                      apply must include only update operators.
   * @param updateOptions the options to apply to the update operation
   * @return the result of the update one operation
   */
  RemoteUpdateResult updateOne(
      final Bson filter,
      final Bson update,
      final RemoteUpdateOptions updateOptions);

  /**
   * Update all documents in the collection according to the specified arguments.
   *
   * @param filter a document describing the query filter, which may not be null.
   * @param update a document describing the update, which may not be null. The update to
   *               apply must include only update operators.
   * @return the result of the update many operation
   */
  RemoteUpdateResult updateMany(final Bson filter, final Bson update);

  /**
   * Update all documents in the collection according to the specified arguments.
   *
   * @param filter        a document describing the query filter, which may not be null.
   * @param update        a document describing the update, which may not be null. The update to
   *                     apply must include only update operators.
   * @param updateOptions the options to apply to the update operation
   * @return the result of the update many operation
   */
  RemoteUpdateResult updateMany(
      final Bson filter,
      final Bson update,
      final RemoteUpdateOptions updateOptions);

  /**
   * Finds a document in the collection and performs the given update.
   *
   * @param filter the query filter
   * @param update the update document
   * @return the resulting document
   */
  DocumentT findOneAndUpdate(final Bson filter, final Bson update);

  /**
   * Finds a document in the collection and performs the given update.
   *
   * @param filter      the query filter
   * @param update      the update document
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return the resulting document
   */
  <ResultT> ResultT findOneAndUpdate(final Bson filter,
                                     final Bson update,
                                     final Class<ResultT> resultClass);

  /**
   * Finds a document in the collection and performs the given update.
   *
   * @param filter the query filter
   * @param update the update document
   * @param options A RemoteFindOneAndModifyOptions struct
   * @return the resulting document
   */
  DocumentT findOneAndUpdate(final Bson filter,
                             final Bson update,
                             final RemoteFindOneAndModifyOptions options);

  /**
   * Finds a document in the collection and performs the given update.
   *
   * @param filter      the query filter
   * @param update      the update document
   * @param options     A RemoteFindOneAndModifyOptions struct
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return the resulting document
   */
  <ResultT> ResultT findOneAndUpdate(
          final Bson filter,
          final Bson update,
          final RemoteFindOneAndModifyOptions options,
          final Class<ResultT> resultClass);

  /**
   * Finds a document in the collection and replaces it with the given document
   *
   * @param filter the query filter
   * @param replacement the document to replace the matched document with
   * @return the resulting document
   */
  DocumentT findOneAndReplace(final Bson filter, final Bson replacement);

  /**
   * Finds a document in the collection and replaces it with the given document
   *
   * @param filter the query filter
   * @param replacement the document to replace the matched document with
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return the resulting document
   */
  <ResultT> ResultT findOneAndReplace(final Bson filter,
                                      final Bson replacement,
                                      final Class<ResultT> resultClass);

  /**
   * Finds a document in the collection and replaces it with the given document
   *
   * @param filter the query filter
   * @param replacement the document to replace the matched document with
   * @param options A RemoteFindOneAndModifyOptions struct
   * @return the resulting document
   */
  DocumentT findOneAndReplace(final Bson filter,
                              final Bson replacement,
                              final RemoteFindOneAndModifyOptions options);

  /**
   * Finds a document in the collection and replaces it with the given document
   *
   * @param filter the query filter
   * @param replacement the document to replace the matched document with
   * @param options     A RemoteFindOneAndModifyOptions struct
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return the resulting document
   */
  <ResultT> ResultT findOneAndReplace(
          final Bson filter,
          final Bson replacement,
          final RemoteFindOneAndModifyOptions options,
          final Class<ResultT> resultClass);

  /**
   * Finds a document in the collection and delete it.
   *
   * @param filter the query filter
   * @return the resulting document
   */
  DocumentT findOneAndDelete(final Bson filter);

  /**
   * Finds a document in the collection and delete it.
   *
   * @param filter      the query filter
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return the resulting document
   */
  <ResultT> ResultT findOneAndDelete(final Bson filter,
                                     final Class<ResultT> resultClass);

  /**
   * Finds a document in the collection and delete it.
   *
   * @param filter the query filter
   * @param options A RemoteFindOneAndModifyOptions struct
   * @return the resulting document
   */
  DocumentT findOneAndDelete(final Bson filter,
                             final RemoteFindOneAndModifyOptions options);

  /**
   * Finds a document in the collection and delete it.
   *
   * @param filter      the query filter
   * @param options     A RemoteFindOneAndModifyOptions struct
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return the resulting document
   */
  <ResultT> ResultT findOneAndDelete(
          final Bson filter,
          final RemoteFindOneAndModifyOptions options,
          final Class<ResultT> resultClass);

  /**
   * Watches a collection. The resulting stream will be notified of all events on this collection
   * that the active user is authorized to see based on the configured MongoDB rules.
   *
   * @return the stream of change events.
   */
  Stream<ChangeEvent<DocumentT>> watch() throws InterruptedException, IOException;

  /**
   * Watches specified IDs in a collection.  This convenience overload supports the use case
   * of non-{@link BsonValue} instances of {@link ObjectId}.
   * @param ids unique object identifiers of the IDs to watch.
   * @return the stream of change events.
   */
  Stream<ChangeEvent<DocumentT>> watch(final ObjectId... ids)
      throws InterruptedException, IOException;

  /**
   * Watches specified IDs in a collection.
   * @param ids the ids to watch.
   * @return the stream of change events.
   */
  Stream<ChangeEvent<DocumentT>> watch(final BsonValue... ids)
      throws InterruptedException, IOException;

  /**
   * Watches a collection. The provided BSON document will be used as a match expression filter on
   * the change events coming from the stream.
   *
   * See https://docs.mongodb.com/manual/reference/operator/aggregation/match/ for documentation
   * around how to define a match filter.
   *
   * Defining the match expression to filter ChangeEvents is similar to defining the match
   * expression for triggers: https://docs.mongodb.com/stitch/triggers/database-triggers/
   *
   * @return the stream of change events.
   */
  Stream<ChangeEvent<DocumentT>> watchWithFilter(final BsonDocument matchFilter)
      throws InterruptedException, IOException;


  /**
   * Watches specified IDs in a collection. Requests a stream where the full document of update
   * events, and several other unnecessary fields are omitted from the change event objects
   * returned by the server. This can save on network usage when watching large documents
   *
   * This convenience overload supports the use case
   * of non-{@link BsonValue} instances of {@link ObjectId} by wrapping them in
   * {@link BsonObjectId} instances for the user.
   *
   * @param ids unique object identifiers of the IDs to watch.
   * @return the stream of change events.
   */
  Stream<CompactChangeEvent<DocumentT>> watchCompact(final ObjectId... ids)
      throws InterruptedException, IOException;

  /**
   * Watches specified IDs in a collection. Requests a stream where the full document of update
   * events, and several other unnecessary fields are omitted from the change event objects
   * returned by the server. This can save on network usage when watching large documents
   *
   * @param ids the ids to watch.
   * @return the stream of change events.
   */
  Stream<CompactChangeEvent<DocumentT>> watchCompact(final BsonValue... ids)
      throws InterruptedException, IOException;

  /**
   * A set of synchronization related operations at the collection level.
   *
   * @return set of sync operations for this collection
  */
  CoreSync<DocumentT> sync();
}
