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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.android.services.mongodb.remote.Sync;
import com.mongodb.stitch.android.services.mongodb.remote.SyncAggregateIterable;
import com.mongodb.stitch.android.services.mongodb.remote.SyncFindIterable;
import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler;
import com.mongodb.stitch.core.services.mongodb.remote.sync.CoreSync;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncCountOptions;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncDeleteResult;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncInsertManyResult;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncInsertOneResult;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncUpdateOptions;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncUpdateResult;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.conversions.Bson;

public class SyncImpl<DocumentT> implements Sync<DocumentT> {
  private final CoreSync<DocumentT> proxy;
  private final TaskDispatcher dispatcher;

  SyncImpl(final CoreSync<DocumentT> proxy,
           final TaskDispatcher dispatcher) {
    this.proxy = proxy;
    this.dispatcher = dispatcher;
  }

  @Override
  public void configure(@NonNull final ConflictHandler<DocumentT> conflictHandler,
                        @Nullable final ChangeEventListener<DocumentT> changeEventListener,
                        @Nullable final ExceptionListener exceptionListener) {
    this.proxy.configure(conflictHandler, changeEventListener, exceptionListener);
  }

  @Override
  public void syncOne(final BsonValue id) {
    proxy.syncOne(id);
  }

  @Override
  public void syncMany(final BsonValue... ids) {
    proxy.syncMany(ids);
  }

  @Override
  public void desyncOne(final BsonValue id) {
    proxy.desyncOne(id);
  }

  @Override
  public void desyncMany(final BsonValue... ids) {
    proxy.desyncMany(ids);
  }

  @Override
  public Set<BsonValue> getSyncedIds() {
    return this.proxy.getSyncedIds();
  }

  @Override
  public Set<BsonValue> getPausedDocumentIds() {
    return this.proxy.getPausedDocumentIds();
  }

  @Override
  public boolean resumeSyncForDocument(@NonNull final BsonValue documentId) {
    return this.proxy.resumeSyncForDocument(documentId);
  }

  @Override
  public Task<Long> count() {
    return this.count(new BsonDocument());
  }

  @Override
  public Task<Long> count(final Bson filter) {
    return this.count(filter, new SyncCountOptions());
  }

  @Override
  public Task<Long> count(final Bson filter, final SyncCountOptions options) {
    return this.dispatcher.dispatchTask(new Callable<Long>() {
      @Override
      public Long call() throws Exception {
        return proxy.count(filter, options);
      }
    });
  }

  @Override
  public SyncAggregateIterable<DocumentT> aggregate(final List<? extends Bson> pipeline) {
    return new SyncAggregateIterableImpl<>(this.proxy.aggregate(pipeline), dispatcher);
  }

  @Override
  public <ResultT> SyncAggregateIterable<ResultT> aggregate(final List<? extends Bson> pipeline,
                                                            final Class<ResultT> resultClass) {
    return new SyncAggregateIterableImpl<>(this.proxy.aggregate(pipeline, resultClass), dispatcher);
  }

  @Override
  public SyncFindIterable<DocumentT> find() {
    return new SyncFindIterableImpl<>(proxy.find(), dispatcher);
  }

  @Override
  public SyncFindIterable<DocumentT> find(final Bson filter) {
    return new SyncFindIterableImpl<>(proxy.find(filter), dispatcher);
  }

  @Override
  public <ResultT> SyncFindIterable<ResultT> find(final Class<ResultT> resultClass) {
    return new SyncFindIterableImpl<>(proxy.find(resultClass), dispatcher);
  }

  @Override
  public <ResultT> SyncFindIterable<ResultT> find(final Bson filter,
                                                  final Class<ResultT> resultClass) {
    return new SyncFindIterableImpl<>(proxy.find(filter, resultClass), dispatcher);
  }

  @Override
  public Task<SyncInsertOneResult> insertOne(final DocumentT document) {
    return this.dispatcher.dispatchTask(new Callable<SyncInsertOneResult>() {
      @Override
      public SyncInsertOneResult call() throws Exception {
        return proxy.insertOne(document);
      }
    });
  }

  @Override
  public Task<SyncInsertManyResult> insertMany(final List<DocumentT> documents) {
    return this.dispatcher.dispatchTask(new Callable<SyncInsertManyResult>() {
      @Override
      public SyncInsertManyResult call() throws Exception {
        return proxy.insertMany(documents);
      }
    });
  }

  @Override
  public Task<SyncUpdateResult> updateOne(final Bson filter, final Bson update) {
    return this.updateOne(filter, update, new SyncUpdateOptions());
  }

  @Override
  public Task<SyncUpdateResult> updateOne(
      final Bson filter,
      final Bson update,
      final SyncUpdateOptions updateOptions
  ) {
    return this.dispatcher.dispatchTask(new Callable<SyncUpdateResult>() {
      @Override
      public SyncUpdateResult call() throws Exception {
        return proxy.updateOne(filter, update, updateOptions);
      }
    });
  }

  @Override
  public Task<SyncUpdateResult> updateMany(final Bson filter, final Bson update) {
    return this.updateMany(filter, update, new SyncUpdateOptions());
  }

  @Override
  public Task<SyncUpdateResult> updateMany(
      final Bson filter,
      final Bson update,
      final SyncUpdateOptions updateOptions
  ) {
    return this.dispatcher.dispatchTask(new Callable<SyncUpdateResult>() {
      @Override
      public SyncUpdateResult call() throws Exception {
        return proxy.updateMany(filter, update, updateOptions);
      }
    });
  }

  @Override
  public Task<SyncDeleteResult> deleteOne(final Bson filter) {
    return this.dispatcher.dispatchTask(new Callable<SyncDeleteResult>() {
      @Override
      public SyncDeleteResult call() throws Exception {
        return proxy.deleteOne(filter);
      }
    });
  }

  @Override
  public Task<SyncDeleteResult> deleteMany(final Bson filter) {
    return this.dispatcher.dispatchTask(new Callable<SyncDeleteResult>() {
      @Override
      public SyncDeleteResult call() throws Exception {
        return proxy.deleteMany(filter);
      }
    });
  }
}
