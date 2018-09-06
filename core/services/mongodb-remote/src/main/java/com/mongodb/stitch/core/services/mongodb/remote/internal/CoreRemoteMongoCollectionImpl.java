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

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteCountOptions;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertManyResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertOneResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateOptions;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult;
import java.util.List;
import org.bson.BsonDocument;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

public class CoreRemoteMongoCollectionImpl<DocumentT>
    implements CoreRemoteMongoCollection<DocumentT> {

  private final MongoNamespace namespace;
  private final Class<DocumentT> documentClass;
  private final CoreStitchServiceClient service;
  private final Operations<DocumentT> operations;

  CoreRemoteMongoCollectionImpl(
      final MongoNamespace namespace,
      final Class<DocumentT> documentClass,
      final CoreStitchServiceClient service
  ) {
    notNull("namespace", namespace);
    notNull("documentClass", documentClass);
    this.namespace = namespace;
    this.documentClass = documentClass;
    this.service = service;
    this.operations = new Operations<>(namespace, documentClass, service.getCodecRegistry());
  }

  /**
   * Gets the namespace of this collection.
   *
   * @return the namespace
   */
  public MongoNamespace getNamespace() {
    return namespace;
  }

  /**
   * Get the class of documents stored in this collection.
   *
   * @return the class
   */
  public Class<DocumentT> getDocumentClass() {
    return documentClass;
  }

  /**
   * Get the codec registry for the CoreRemoteMongoCollection.
   *
   * @return the {@link org.bson.codecs.configuration.CodecRegistry}
   */
  public CodecRegistry getCodecRegistry() {
    return service.getCodecRegistry();
  }

  public <NewDocumentT> CoreRemoteMongoCollection<NewDocumentT> withDocumentClass(
      final Class<NewDocumentT> clazz
  ) {
    return new CoreRemoteMongoCollectionImpl<>(namespace, clazz, service);
  }

  public CoreRemoteMongoCollection<DocumentT> withCodecRegistry(final CodecRegistry codecRegistry) {
    return new CoreRemoteMongoCollectionImpl<>(
        namespace, documentClass, service.withCodecRegistry(codecRegistry));
  }

  /**
   * Counts the number of documents in the collection.
   *
   * @return the number of documents in the collection
   */
  public long count() {
    return count(new BsonDocument(), new RemoteCountOptions());
  }

  /**
   * Counts the number of documents in the collection according to the given options.
   *
   * @param filter the query filter
   * @return the number of documents in the collection
   */
  public long count(final Bson filter) {
    return count(filter, new RemoteCountOptions());
  }

  /**
   * Counts the number of documents in the collection according to the given options.
   *
   * @param filter  the query filter
   * @param options the options describing the count
   * @return the number of documents in the collection
   */
  public long count(final Bson filter, final RemoteCountOptions options) {
    return operations.count(filter, options).execute(service);
  }

  /**
   * Finds all documents in the collection.
   *
   * @return the find iterable interface
   */
  public CoreRemoteFindIterable<DocumentT> find() {
    return find(new BsonDocument(), documentClass);
  }

  /**
   * Finds all documents in the collection.
   *
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return the find iterable interface
   */
  public <ResultT> CoreRemoteFindIterable<ResultT> find(final Class<ResultT> resultClass) {
    return find(new BsonDocument(), resultClass);
  }

  /**
   * Finds all documents in the collection.
   *
   * @param filter the query filter
   * @return the find iterable interface
   */
  public CoreRemoteFindIterable<DocumentT> find(final Bson filter) {
    return find(filter, documentClass);
  }

  /**
   * Finds all documents in the collection.
   *
   * @param filter      the query filter
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return the find iterable interface
   */
  public <ResultT> CoreRemoteFindIterable<ResultT> find(
      final Bson filter,
      final Class<ResultT> resultClass
  ) {
    return createFindIterable(filter, resultClass);
  }

  private <ResultT> CoreRemoteFindIterable<ResultT> createFindIterable(
      final Bson filter,
      final Class<ResultT> resultClass
  ) {
    return new CoreRemoteFindIterableImpl<>(
        filter,
        resultClass,
        service,
        operations);
  }

  /**
   * Aggregates documents according to the specified aggregation pipeline.
   *
   * @param pipeline the aggregation pipeline
   * @return an iterable containing the result of the aggregation operation
   */
  public CoreRemoteAggregateIterable<DocumentT> aggregate(final List<? extends Bson> pipeline) {
    return aggregate(pipeline, this.documentClass);
  }

  /**
   * Aggregates documents according to the specified aggregation pipeline.
   *
   * @param pipeline    the aggregation pipeline
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return an iterable containing the result of the aggregation operation
   */
  public <ResultT> CoreRemoteAggregateIterable<ResultT> aggregate(
      final List<? extends Bson> pipeline,
      final Class<ResultT> resultClass
  ) {
    return new CoreRemoteAggregateIterableImpl<>(
        pipeline,
        resultClass,
        service,
        operations);
  }

  /**
   * Inserts the provided document. If the document is missing an identifier, the client should
   * generate one.
   *
   * @param document the document to insert
   * @return the result of the insert one operation
   */
  public RemoteInsertOneResult insertOne(final DocumentT document) {
    return executeInsertOne(document);
  }

  private RemoteInsertOneResult executeInsertOne(final DocumentT document) {
    return operations.insertOne(document).execute(service);
  }

  /**
   * Inserts one or more documents.
   *
   * @param documents the documents to insert
   * @return the result of the insert many operation
   */
  public RemoteInsertManyResult insertMany(final List<? extends DocumentT> documents) {
    return executeInsertMany(documents);
  }

  private RemoteInsertManyResult executeInsertMany(final List<? extends DocumentT> documents) {
    return operations.insertMany(documents).execute(service);
  }

  /**
   * Removes at most one document from the collection that matches the given filter.
   * If no documents match, the collection is not modified.
   *
   * @param filter the query filter to apply the the delete operation
   * @return the result of the remove one operation
   */
  public RemoteDeleteResult deleteOne(final Bson filter) {
    return executeDelete(filter, false);
  }

  /**
   * Removes all documents from the collection that match the given query filter.
   * If no documents match, the collection is not modified.
   *
   * @param filter the query filter to apply the the delete operation
   * @return the result of the remove many operation
   */
  public RemoteDeleteResult deleteMany(final Bson filter) {
    return executeDelete(filter, true);
  }

  private RemoteDeleteResult executeDelete(
      final Bson filter,
      final boolean multi
  ) {
    return multi ? operations.deleteMany(filter).execute(service)
        : operations.deleteOne(filter).execute(service);
  }

  /**
   * Update a single document in the collection according to the specified arguments.
   *
   * @param filter a document describing the query filter, which may not be null.
   * @param update a document describing the update, which may not be null. The update to apply
   *              must include only update operators.
   * @return the result of the update one operation
   */
  public RemoteUpdateResult updateOne(final Bson filter, final Bson update) {
    return updateOne(filter, update, new RemoteUpdateOptions());
  }

  /**
   * Update a single document in the collection according to the specified arguments.
   *
   * @param filter        a document describing the query filter, which may not be null.
   * @param update        a document describing the update, which may not be null. The update to
   *                     apply must include only update operators.
   * @param updateOptions the options to apply to the update operation
   * @return the result of the update one operation
   */
  public RemoteUpdateResult updateOne(
      final Bson filter,
      final Bson update,
      final RemoteUpdateOptions updateOptions
  ) {
    return executeUpdate(filter, update, updateOptions, false);
  }

  /**
   * Update all documents in the collection according to the specified arguments.
   *
   * @param filter a document describing the query filter, which may not be null.
   * @param update a document describing the update, which may not be null. The update to apply
   *              must include only update operators.
   * @return the result of the update many operation
   */
  public RemoteUpdateResult updateMany(final Bson filter, final Bson update) {
    return updateMany(filter, update, new RemoteUpdateOptions());
  }

  /**
   * Update all documents in the collection according to the specified arguments.
   *
   * @param filter        a document describing the query filter, which may not be null.
   * @param update        a document describing the update, which may not be null. The update to
   *                     apply must include only update operators.
   * @param updateOptions the options to apply to the update operation
   * @return the result of the update many operation
   */
  public RemoteUpdateResult updateMany(
      final Bson filter,
      final Bson update,
      final RemoteUpdateOptions updateOptions
  ) {
    return executeUpdate(filter, update, updateOptions, true);
  }

  private RemoteUpdateResult executeUpdate(
      final Bson filter,
      final Bson update,
      final RemoteUpdateOptions updateOptions,
      final boolean multi
  ) {
    return multi ? operations.updateMany(filter, update, updateOptions).execute(service)
        : operations.updateOne(filter, update, updateOptions).execute(service);
  }
}
