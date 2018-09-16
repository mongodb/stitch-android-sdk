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

import com.mongodb.MongoNamespace;
import com.mongodb.client.MongoDatabase;
import com.mongodb.stitch.core.internal.net.NetworkMonitor;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteCountOptions;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertManyResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertOneResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateOptions;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteAggregateIterable;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteFindIterable;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoCollection;
import com.mongodb.stitch.core.services.mongodb.sync.ChangeEventListener;
import com.mongodb.stitch.core.services.mongodb.sync.CoreSync;
import com.mongodb.stitch.core.services.mongodb.sync.DocumentSynchronizationConfig;
import com.mongodb.stitch.core.services.mongodb.sync.SyncConflictResolver;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;


public class CoreSyncMongoCollectionImpl<DocumentT> implements CoreSyncMongoCollection<DocumentT> {

  private final CoreRemoteMongoCollection<DocumentT> proxy;
  private final DataSynchronizer dataSynchronizer;
  private final NetworkMonitor networkMonitor;
  private final CoreStitchServiceClient service;
  private final MongoDatabase tempDb;
  private final CoreSync<DocumentT> sync;

  CoreSyncMongoCollectionImpl(
      final CoreRemoteMongoCollection<DocumentT> proxy,
      final DataSynchronizer dataSynchronizer,
      final NetworkMonitor networkMonitor,
      final CoreStitchServiceClient service,
      final MongoDatabase tempDb
  ) {
    this.proxy = proxy;
    this.dataSynchronizer = dataSynchronizer;
    this.networkMonitor = networkMonitor;
    this.sync = new CoreSyncImpl<>(
        proxy.getNamespace(),
        proxy.getDocumentClass(),
        dataSynchronizer,
        service,
        new SyncOperations<>(
                proxy.getNamespace(),
                proxy.getDocumentClass(),
                dataSynchronizer,
                networkMonitor,
                proxy.getCodecRegistry(),
                proxy,
                tempDb),
        proxy.getCodecRegistry()
    );

    this.service = service.withCodecRegistry(proxy.getCodecRegistry());
    this.tempDb = tempDb;
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
   * Get the codec registry for the CoreSyncMongoCollection.
   *
   * @return the {@link org.bson.codecs.configuration.CodecRegistry}
   */
  public CodecRegistry getCodecRegistry() {
    return proxy.getCodecRegistry();
  }

  public <NewDocumentT> CoreSyncMongoCollection<NewDocumentT> withDocumentClass(
      final Class<NewDocumentT> clazz
  ) {
    return new CoreSyncMongoCollectionImpl<>(
        proxy.withDocumentClass(clazz),
        dataSynchronizer,
        networkMonitor,
        service,
        tempDb);
  }

  public CoreSyncMongoCollection<DocumentT> withCodecRegistry(final CodecRegistry codecRegistry) {
    return new CoreSyncMongoCollectionImpl<>(
        proxy.withCodecRegistry(codecRegistry),
        dataSynchronizer,
        networkMonitor,
        service,
        tempDb);
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
  public CoreRemoteFindIterable<DocumentT> find() {
    return find(new BsonDocument(), proxy.getDocumentClass());
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
    return find(filter, proxy.getDocumentClass());
  }

    @Override
    public <ResultT> CoreRemoteFindIterable<ResultT> find(Bson filter, Class<ResultT> resultClass) {
        return this.proxy.find(filter, resultClass);
    }

    /**
   * Aggregates documents according to the specified aggregation pipeline.
   *
   * @param pipeline the aggregation pipeline
   * @return an iterable containing the result of the aggregation operation
   */
  public CoreRemoteAggregateIterable<DocumentT> aggregate(final List<? extends Bson> pipeline) {
    return proxy.aggregate(pipeline);
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
    return proxy.aggregate(pipeline, resultClass);
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
   * Removes at most one document from the collection that matches the given filter.
   * If no documents match, the collection is not modified.
   *
   * @param filter the query filter to apply the the delete operation
   * @return the result of the remove one operation
   */
  public RemoteDeleteResult deleteOne(final Bson filter) {
    return proxy.deleteOne(filter);
  }

  /**
   * Removes all documents from the collection that match the given query filter.
   * If no documents match, the collection is not modified.
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
   *              must include only update operators.
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
   *                     apply must include only update operators.
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
   *              must include only update operators.
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
   *                     apply must include only update operators.
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

  /**
   * A set of synchronization related operations at the collection level.
   *
   * @return a set of sync related operations
   */
  public CoreSync<DocumentT> sync() {
      return this.sync;
  }
}
