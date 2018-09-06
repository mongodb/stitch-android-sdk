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

package com.mongodb.stitch.android.services.mongodb.sync.internal;

import com.google.android.gms.tasks.Task;
import com.mongodb.MongoNamespace;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteAggregateIterable;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteFindIterable;
import com.mongodb.stitch.android.services.mongodb.remote.internal.RemoteAggregateIterableImpl;
import com.mongodb.stitch.android.services.mongodb.remote.internal.RemoteFindIterableImpl;
import com.mongodb.stitch.android.services.mongodb.sync.SyncMongoCollection;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteCountOptions;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertManyResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertOneResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateOptions;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult;
import com.mongodb.stitch.core.services.mongodb.sync.ChangeEventListener;
import com.mongodb.stitch.core.services.mongodb.sync.DocumentSynchronizationConfig;
import com.mongodb.stitch.core.services.mongodb.sync.SyncConflictResolver;
import com.mongodb.stitch.core.services.mongodb.sync.internal.CoreSyncMongoCollection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import org.bson.BsonValue;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

public final class SyncMongoCollectionImpl<DocumentT>
    implements SyncMongoCollection<DocumentT> {

  private final CoreSyncMongoCollection<DocumentT> proxy;
  private final TaskDispatcher dispatcher;

  SyncMongoCollectionImpl(
      final CoreSyncMongoCollection<DocumentT> coll,
      final TaskDispatcher dispatcher
  ) {
    this.proxy = coll;
    this.dispatcher = dispatcher;
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
   * Get the codec registry for the SyncMongoCollection.
   *
   * @return the {@link org.bson.codecs.configuration.CodecRegistry}
   */
  public CodecRegistry getCodecRegistry() {
    return proxy.getCodecRegistry();
  }

  public <NewDocumentT> SyncMongoCollection<NewDocumentT> withDocumentClass(
      final Class<NewDocumentT> clazz
  ) {
    return new SyncMongoCollectionImpl<>(proxy.withDocumentClass(clazz), dispatcher);
  }

  public SyncMongoCollection<DocumentT> withCodecRegistry(final CodecRegistry codecRegistry) {
    return new SyncMongoCollectionImpl<>(proxy.withCodecRegistry(codecRegistry), dispatcher);
  }

  /**
   * Counts the number of documents in the collection.
   *
   * @return a task containing the number of documents in the collection
   */
  public Task<Long> count() {
    return dispatcher.dispatchTask(new Callable<Long>() {
      @Override
      public Long call() {
        return proxy.count();
      }
    });
  }

  /**
   * Counts the number of documents in the collection according to the given options.
   *
   * @param filter the query filter
   * @return a task containing the number of documents in the collection
   */
  public Task<Long> count(final Bson filter) {
    return dispatcher.dispatchTask(new Callable<Long>() {
      @Override
      public Long call() {
        return proxy.count(filter);
      }
    });
  }

  /**
   * Counts the number of documents in the collection according to the given options.
   *
   * @param filter  the query filter
   * @param options the options describing the count
   * @return a task containing the number of documents in the collection
   */
  public Task<Long> count(final Bson filter, final RemoteCountOptions options) {
    return dispatcher.dispatchTask(new Callable<Long>() {
      @Override
      public Long call() {
        return proxy.count(filter, options);
      }
    });
  }

  /**
   * Finds all documents in the collection.
   *
   * @return the find iterable interface
   */
  public RemoteFindIterable<DocumentT> find() {
    return new RemoteFindIterableImpl<>(proxy.find(), dispatcher);
  }

  /**
   * Finds all documents in the collection.
   *
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return the find iterable interface
   */
  public <ResultT> RemoteFindIterable<ResultT> find(final Class<ResultT> resultClass) {
    return new RemoteFindIterableImpl<>(proxy.find(resultClass), dispatcher);
  }

  /**
   * Finds all documents in the collection.
   *
   * @param filter the query filter
   * @return the find iterable interface
   */
  public RemoteFindIterable<DocumentT> find(final Bson filter) {
    return new RemoteFindIterableImpl<>(proxy.find(filter), dispatcher);
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
    return new RemoteFindIterableImpl<>(proxy.find(filter, resultClass), dispatcher);
  }

  /**
   * Aggregates documents according to the specified aggregation pipeline.
   *
   * @param pipeline the aggregation pipeline
   * @return an iterable containing the result of the aggregation operation
   */
  public RemoteAggregateIterable<DocumentT> aggregate(final List<? extends Bson> pipeline) {
    return new RemoteAggregateIterableImpl<>(proxy.aggregate(pipeline), dispatcher);
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
    return new RemoteAggregateIterableImpl<>(proxy.aggregate(pipeline, resultClass), dispatcher);
  }

  /**
   * Inserts the provided document. If the document is missing an identifier, the client should
   * generate one.
   *
   * @param document the document to insert
   * @return a task containing the result of the insert one operation
   */
  public Task<RemoteInsertOneResult> insertOne(final DocumentT document) {
    return dispatcher.dispatchTask(new Callable<RemoteInsertOneResult>() {
      @Override
      public RemoteInsertOneResult call() {
        return proxy.insertOne(document);
      }
    });
  }

  /**
   * Inserts one or more documents.
   *
   * @param documents the documents to insert
   * @return a task containing the result of the insert many operation
   */
  public Task<RemoteInsertManyResult> insertMany(final List<? extends DocumentT> documents) {
    return dispatcher.dispatchTask(new Callable<RemoteInsertManyResult>() {
      @Override
      public RemoteInsertManyResult call() {
        return proxy.insertMany(documents);
      }
    });
  }

  /**
   * Removes at most one document from the collection that matches the given filter.  If no
   * documents match, the collection is not modified.
   *
   * @param filter the query filter to apply the the delete operation
   * @return a task containing the result of the remove one operation
   */
  public Task<RemoteDeleteResult> deleteOne(final Bson filter) {
    return dispatcher.dispatchTask(new Callable<RemoteDeleteResult>() {
      @Override
      public RemoteDeleteResult call() {
        return proxy.deleteOne(filter);
      }
    });
  }

  /**
   * Removes all documents from the collection that match the given query filter.  If no
   * documents match, the collection is not modified.
   *
   * @param filter the query filter to apply the the delete operation
   * @return a task containing the result of the remove many operation
   */
  public Task<RemoteDeleteResult> deleteMany(final Bson filter) {
    return dispatcher.dispatchTask(new Callable<RemoteDeleteResult>() {
      @Override
      public RemoteDeleteResult call() {
        return proxy.deleteMany(filter);
      }
    });
  }

  /**
   * Update a single document in the collection according to the specified arguments.
   *
   * @param filter a document describing the query filter, which may not be null.
   * @param update a document describing the update, which may not be null. The update to apply
   *               must include only update operators.
   * @return a task containing the result of the update one operation
   */
  public Task<RemoteUpdateResult> updateOne(final Bson filter, final Bson update) {
    return dispatcher.dispatchTask(new Callable<RemoteUpdateResult>() {
      @Override
      public RemoteUpdateResult call() {
        return proxy.updateOne(filter, update);
      }
    });
  }

  /**
   * Update a single document in the collection according to the specified arguments.
   *
   * @param filter        a document describing the query filter, which may not be null.
   * @param update        a document describing the update, which may not be null. The update to
   *                      apply must include only update operators.
   * @param updateOptions the options to apply to the update operation
   * @return a task containing the result of the update one operation
   */
  public Task<RemoteUpdateResult> updateOne(
      final Bson filter,
      final Bson update,
      final RemoteUpdateOptions updateOptions
  ) {
    return dispatcher.dispatchTask(new Callable<RemoteUpdateResult>() {
      @Override
      public RemoteUpdateResult call() {
        return proxy.updateOne(filter, update, updateOptions);
      }
    });
  }

  /**
   * Update all documents in the collection according to the specified arguments.
   *
   * @param filter a document describing the query filter, which may not be null.
   * @param update a document describing the update, which may not be null. The update to apply
   *               must include only update operators.
   * @return a task containing the result of the update many operation
   */
  public Task<RemoteUpdateResult> updateMany(final Bson filter, final Bson update) {
    return dispatcher.dispatchTask(new Callable<RemoteUpdateResult>() {
      @Override
      public RemoteUpdateResult call() {
        return proxy.updateMany(filter, update);
      }
    });
  }

  /**
   * Update all documents in the collection according to the specified arguments.
   *
   * @param filter        a document describing the query filter, which may not be null.
   * @param update        a document describing the update, which may not be null. The update to
   *                      apply must include only update operators.
   * @param updateOptions the options to apply to the update operation
   * @return a task containing the result of the update many operation
   */
  public Task<RemoteUpdateResult> updateMany(
      final Bson filter,
      final Bson update,
      final RemoteUpdateOptions updateOptions
  ) {
    return dispatcher.dispatchTask(new Callable<RemoteUpdateResult>() {
      @Override
      public RemoteUpdateResult call() {
        return proxy.updateMany(filter, update, updateOptions);
      }
    });
  }

  /**
   * Requests that the given document _id be synchronized.
   * @param documentId the document _id to synchronize.
   * @param conflictResolver the conflict resolver to invoke when a conflict happens between local
   *                         and remote events.
   */
  public void sync(
      final BsonValue documentId,
      final SyncConflictResolver<DocumentT> conflictResolver
  ) {
    proxy.sync(documentId, conflictResolver);
  }


  /**
   * Requests that the given document _id be synchronized.
   * @param documentId the document _id to synchronize.
   * @param conflictResolver the conflict resolver to invoke when a conflict happens between local
   *                         and remote events.
   * @param eventListener the event listener to invoke when a a change event happens for the
   *                      document.
   */
  public void sync(
      final BsonValue documentId,
      final SyncConflictResolver<DocumentT> conflictResolver,
      final ChangeEventListener<DocumentT> eventListener) {
    proxy.sync(documentId, conflictResolver, eventListener);
  }

  /**
   * Returns the set of synchronized documents in a namespace.
   *
   * @return the set of synchronized documents in a namespace.
   */
  @Override
  public Set<DocumentSynchronizationConfig> getSynchronizedDocuments() {
    return proxy.getSynchronizedDocuments();
  }

  /**
   * Stops synchronizing the given document _id. Any uncommitted writes will be lost.
   *
   * @param documentId the _id of the document to desynchronize.
   */
  @Override
  public void desync(final BsonValue documentId) {
    proxy.desync(documentId);
  }

  /**
   * Finds a single document by the given id. It is first searched for in the local synchronized
   * cache and if not found and there is internet connectivity, it is searched for remotely.
   *
   * @param documentId the _id of the document to search for.
   * @return a task containing the document if found locally or remotely.
   */
  public Task<DocumentT> findOneById(final BsonValue documentId) {
    return dispatcher.dispatchTask(new Callable<DocumentT>() {
      @Override
      public DocumentT call() {
        return proxy.findOneById(documentId);
      }
    });
  }

  /**
   * Finds a single document by the given id. It is first searched for in the local synchronized
   * cache and if not found and there is internet connectivity, it is searched for remotely.
   *
   * @param documentId the _id of the document to search for.
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return a task containing the document if found locally or remotely.
   */
  public <ResultT> Task<ResultT> findOneById(
      final BsonValue documentId,
      final Class<ResultT> resultClass
  ) {
    return dispatcher.dispatchTask(new Callable<ResultT>() {
      @Override
      public ResultT call() {
        return proxy.findOneById(documentId, resultClass);
      }
    });
  }

  /**
   * Updates a document by the given id. It is first searched for in the local synchronized cache
   * and if not found and there is internet connectivity, it is searched for remotely.
   *
   * @param documentId the _id of the document to search for.
   * @param update the update specifier.
   * @return a task containing the result of the local or remote update.
   */
  public Task<RemoteUpdateResult> updateOneById(
      final BsonValue documentId,
      final Bson update
  ) {
    return dispatcher.dispatchTask(new Callable<RemoteUpdateResult>() {
      @Override
      public RemoteUpdateResult call() {
        return proxy.updateOneById(documentId, update);
      }
    });
  }

  /**
   * Inserts a single document and begins to synchronize it.
   *
   * @param document the document to insert and synchronize.
   * @param conflictResolver the conflict resolver to invoke when a conflict happens between local
   *                         and remote events.
   * @return the result of the insertion.
   */
  public RemoteInsertOneResult insertOneAndSync(
      final DocumentT document,
      final SyncConflictResolver<DocumentT> conflictResolver
  ) {
    return proxy.insertOneAndSync(document, conflictResolver);
  }

  /**
   * Inserts a single document and begins to synchronize it.
   *
   * @param document the document to insert and synchronize.
   * @param conflictResolver the conflict resolver to invoke when a conflict happens between local
   *                         and remote events.
   * @param eventListener the event listener to invoke when a a change event happens for the
   *                      document.
   * @return the result of the insertion.
   */
  public RemoteInsertOneResult insertOneAndSync(
      final DocumentT document,
      final SyncConflictResolver<DocumentT> conflictResolver,
      final ChangeEventListener<DocumentT> eventListener
  ) {
    return proxy.insertOneAndSync(document, conflictResolver, eventListener);
  }

  /**
   * Deletes a single document by the given id. It is first searched for in the local synchronized
   * cache and if not found and there is internet connectivity, it is searched for remotely.
   *
   * @param documentId the _id of the document to search for.
   * @return a task containing the result of the local or remote update.
   */
  public Task<RemoteDeleteResult> deleteOneById(final BsonValue documentId) {
    return dispatcher.dispatchTask(new Callable<RemoteDeleteResult>() {
      @Override
      public RemoteDeleteResult call() {
        return proxy.deleteOneById(documentId);
      }
    });
  }
}
