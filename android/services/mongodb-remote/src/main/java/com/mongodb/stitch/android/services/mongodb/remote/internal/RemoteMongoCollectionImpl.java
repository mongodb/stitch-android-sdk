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

package com.mongodb.stitch.android.services.mongodb.remote.internal;

import com.google.android.gms.tasks.Task;
import com.mongodb.MongoNamespace;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteAggregateIterable;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteFindIterable;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection;
import com.mongodb.stitch.android.services.mongodb.remote.Sync;
import com.mongodb.stitch.core.internal.net.Stream;
import com.mongodb.stitch.core.services.mongodb.remote.ChangeEvent;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteCountOptions;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertManyResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertOneResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateOptions;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoCollection;
import java.util.List;
import java.util.concurrent.Callable;

import org.bson.BsonValue;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

public final class RemoteMongoCollectionImpl<DocumentT>
    implements RemoteMongoCollection<DocumentT> {

  private final CoreRemoteMongoCollection<DocumentT> proxy;
  private final TaskDispatcher dispatcher;
  private final Sync<DocumentT> sync;

  RemoteMongoCollectionImpl(
      final CoreRemoteMongoCollection<DocumentT> coll,
      final TaskDispatcher dispatcher
  ) {
    this.proxy = coll;
    this.dispatcher = dispatcher;
    this.sync = new SyncImpl<>(this.proxy.sync(), this.dispatcher);
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
   * @return the {@link CodecRegistry}
   */
  public CodecRegistry getCodecRegistry() {
    return proxy.getCodecRegistry();
  }

  public <NewDocumentT> RemoteMongoCollection<NewDocumentT> withDocumentClass(
      final Class<NewDocumentT> clazz
  ) {
    return new RemoteMongoCollectionImpl<>(proxy.withDocumentClass(clazz), dispatcher);
  }

  public RemoteMongoCollection<DocumentT> withCodecRegistry(final CodecRegistry codecRegistry) {
    return new RemoteMongoCollectionImpl<>(proxy.withCodecRegistry(codecRegistry), dispatcher);
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

  @Override
  public Task<Stream<ChangeEvent<DocumentT>>> watch(final ObjectId... ids) {
    return dispatcher.dispatchTask(new Callable<Stream<ChangeEvent<DocumentT>>>() {
      @Override
      public Stream<ChangeEvent<DocumentT>> call () {
        return proxy.watch(ids);
      }
    });
  }

  @Override
  public Task<Stream<ChangeEvent<DocumentT>>> watch(final BsonValue... ids) {
    return dispatcher.dispatchTask(new Callable<Stream<ChangeEvent<DocumentT>>>() {
      @Override
      public Stream<ChangeEvent<DocumentT>> call() throws Exception {
        return proxy.watch(ids);
      }
    });
  }

  @Override
  public Sync<DocumentT> sync() {
    return this.sync;
  }
}
