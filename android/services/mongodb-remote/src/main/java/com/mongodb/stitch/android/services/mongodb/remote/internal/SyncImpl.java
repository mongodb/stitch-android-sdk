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

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.android.services.mongodb.remote.Sync;
import com.mongodb.stitch.android.services.mongodb.remote.SyncFindIterable;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertOneResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler;
import com.mongodb.stitch.core.services.mongodb.remote.sync.CoreSync;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ErrorListener;

import java.util.Set;
import java.util.concurrent.Callable;

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
                        final ChangeEventListener<DocumentT> changeEventListener,
                        final ErrorListener errorListener) {
    this.proxy.configure(conflictHandler, changeEventListener, errorListener);
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
  public Task<DocumentT> findOneById(final BsonValue documentId) {
    return this.dispatcher.dispatchTask(new Callable<DocumentT>() {
      @Override
      public DocumentT call() throws Exception {
        return proxy.findOneById(documentId);
      }
    });
  }

  @Override
  public <ResultT> Task<ResultT> findOneById(final BsonValue documentId,
                                             final Class<ResultT> resultClass) {
    return this.dispatcher.dispatchTask(new Callable<ResultT>() {
      @Override
      public ResultT call() throws Exception {
        return proxy.findOneById(documentId, resultClass);
      }
    });
  }

  @Override
  public Task<RemoteDeleteResult> deleteOneById(final BsonValue documentId) {
    return this.dispatcher.dispatchTask(new Callable<RemoteDeleteResult>() {
      @Override
      public RemoteDeleteResult call() throws Exception {
        return proxy.deleteOneById(documentId);
      }
    });
  }

  @Override
  public Task<RemoteInsertOneResult> insertOneAndSync(final DocumentT document) {
    return this.dispatcher.dispatchTask(new Callable<RemoteInsertOneResult>() {
      @Override
      public RemoteInsertOneResult call() throws Exception {
        return proxy.insertOneAndSync(document);
      }
    });
  }

  @Override
  public Task<RemoteUpdateResult> updateOneById(final BsonValue documentId, final Bson update) {
    return this.dispatcher.dispatchTask(new Callable<RemoteUpdateResult>() {
      @Override
      public RemoteUpdateResult call() throws Exception {
        return proxy.updateOneById(documentId, update);
      }
    });
  }
}
