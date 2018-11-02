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

package com.mongodb.stitch.server.services.mongodb.remote.internal;

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteCountOptions;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertManyResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertOneResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateOptions;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoCollection;
import com.mongodb.stitch.server.services.mongodb.remote.RemoteAggregateIterable;
import com.mongodb.stitch.server.services.mongodb.remote.RemoteFindIterable;
import com.mongodb.stitch.server.services.mongodb.remote.RemoteMongoCollection;

import java.util.List;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

public final class RemoteMongoCollectionImpl<DocumentT>
    implements RemoteMongoCollection<DocumentT> {

  private final CoreRemoteMongoCollection<DocumentT> proxy;

  RemoteMongoCollectionImpl(final CoreRemoteMongoCollection<DocumentT> coll) {
    this.proxy = coll;
  }

  /**
   * Gets the namespace of this collection.
   *
   * @return the namespace
   */
  public MongoNamespace getNamespace() {
    return proxy.getNamespace();
  }

  /**
   * Get the class of documents stored in this collection.
   *
   * @return the class
   */
  public Class<DocumentT> getDocumentClass() {
    return proxy.getDocumentClass();
  }

  /**
   * Get the codec registry for the RemoteMongoCollection.
   *
   * @return the {@link org.bson.codecs.configuration.CodecRegistry}
   */
  public CodecRegistry getCodecRegistry() {
    return proxy.getCodecRegistry();
  }

  public <NewDocumentT> RemoteMongoCollection<NewDocumentT> withDocumentClass(
      final Class<NewDocumentT> clazz
  ) {
    return new RemoteMongoCollectionImpl<>(proxy.withDocumentClass(clazz));
  }

  public RemoteMongoCollection<DocumentT> withCodecRegistry(final CodecRegistry codecRegistry) {
    return new RemoteMongoCollectionImpl<>(proxy.withCodecRegistry(codecRegistry));
  }

  /**
   * Counts the number of documents in the collection.
   *
   * @return the number of documents in the collection
   */
  public long count() {
    return proxy.count();
  }

  /**
   * Counts the number of documents in the collection according to the given options.
   *
   * @param filter the query filter
   * @return the number of documents in the collection
   */
  public long count(final Bson filter) {
    return proxy.count(filter);
  }

  /**
   * Counts the number of documents in the collection according to the given options.
   *
   * @param filter  the query filter
   * @param options the options describing the count
   * @return the number of documents in the collection
   */
  public long count(final Bson filter, final RemoteCountOptions options) {
    return proxy.count(filter, options);
  }

  /**
   * Finds all documents in the collection.
   *
   * @return the find iterable interface
   */
  public RemoteFindIterable<DocumentT> find() {
    return new RemoteFindIterableImpl<>(proxy.find());
  }

  /**
   * Finds all documents in the collection.
   *
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return the find iterable interface
   */
  public <ResultT> RemoteFindIterable<ResultT> find(final Class<ResultT> resultClass) {
    return new RemoteFindIterableImpl<>(proxy.find(resultClass));
  }

  /**
   * Finds all documents in the collection.
   *
   * @param filter the query filter
   * @return the find iterable interface
   */
  public RemoteFindIterable<DocumentT> find(final Bson filter) {
    return new RemoteFindIterableImpl<>(proxy.find(filter));
  }

  /**
   * Finds all documents in the collection.
   *
   * @param filter      the query filter
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return the find iterable interface
   */
  public <ResultT> RemoteFindIterable<ResultT> find(
      final Bson filter,
      final Class<ResultT> resultClass
  ) {
    return new RemoteFindIterableImpl<>(proxy.find(filter, resultClass));
  }

  /**
   * Aggregates documents according to the specified aggregation pipeline.
   *
   * @param pipeline the aggregation pipeline
   * @return an iterable containing the result of the aggregation operation
   */
  public RemoteAggregateIterable<DocumentT> aggregate(final List<? extends Bson> pipeline) {
    return new RemoteAggregateIterableImpl<>(proxy.aggregate(pipeline));
  }

  /**
   * Aggregates documents according to the specified aggregation pipeline.
   *
   * @param pipeline    the aggregation pipeline
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return an iterable containing the result of the aggregation operation
   */
  public <ResultT> RemoteAggregateIterable<ResultT> aggregate(
      final List<? extends Bson> pipeline,
      final Class<ResultT> resultClass
  ) {
    return new RemoteAggregateIterableImpl<>(proxy.aggregate(pipeline, resultClass));
  }

  /**
   * Inserts the provided document. If the document is missing an identifier, the client should
   * generate one.
   *
   * @param document the document to insert
   * @return the result of the insert one operation
   */
  public RemoteInsertOneResult insertOne(final DocumentT document) {
    return proxy.insertOne(document);
  }

  /**
   * Inserts one or more documents.
   *
   * @param documents the documents to insert
   * @return the result of the insert many operation
   */
  public RemoteInsertManyResult insertMany(final List<? extends DocumentT> documents) {
    return proxy.insertMany(documents);
  }

  /**
   * Removes at most one document from the collection that matches the given filter.  If no
   * documents match, the collection is not modified.
   *
   * @param filter the query filter to apply the the delete operation
   * @return the result of the remove one operation
   */
  public RemoteDeleteResult deleteOne(final Bson filter) {
    return proxy.deleteOne(filter);
  }

  /**
   * Removes all documents from the collection that match the given query filter.  If no
   * documents match, the collection is not modified.
   *
   * @param filter the query filter to apply the the delete operation
   * @return the result of the remove many operation
   */
  public RemoteDeleteResult deleteMany(final Bson filter) {
    return proxy.deleteMany(filter);
  }

  /**
   * Update a single document in the collection according to the specified arguments.
   *
   * @param filter a document describing the query filter, which may not be null.
   * @param update a document describing the update, which may not be null. The update to apply
   *               must include only update operators.
   * @return the result of the update one operation
   */
  public RemoteUpdateResult updateOne(final Bson filter, final Bson update) {
    return proxy.updateOne(filter, update);
  }

  /**
   * Update a single document in the collection according to the specified arguments.
   *
   * @param filter        a document describing the query filter, which may not be null.
   * @param update        a document describing the update, which may not be null. The update to
   *                      apply must include only update operators.
   * @param updateOptions the options to apply to the update operation
   * @return the result of the update one operation
   */
  public RemoteUpdateResult updateOne(
      final Bson filter,
      final Bson update,
      final RemoteUpdateOptions updateOptions
  ) {
    return proxy.updateOne(filter, update, updateOptions);
  }

  /**
   * Update all documents in the collection according to the specified arguments.
   *
   * @param filter a document describing the query filter, which may not be null.
   * @param update a document describing the update, which may not be null. The update to apply
   *               must include only update operators.
   * @return the result of the update many operation
   */
  public RemoteUpdateResult updateMany(final Bson filter, final Bson update) {
    return proxy.updateMany(filter, update);
  }

  /**
   * Update all documents in the collection according to the specified arguments.
   *
   * @param filter        a document describing the query filter, which may not be null.
   * @param update        a document describing the update, which may not be null. The update to
   *                      apply must include only update operators.
   * @param updateOptions the options to apply to the update operation
   * @return the result of the update many operation
   */
  public RemoteUpdateResult updateMany(
      final Bson filter,
      final Bson update,
      final RemoteUpdateOptions updateOptions
  ) {
    return proxy.updateMany(filter, update, updateOptions);
  }
}
