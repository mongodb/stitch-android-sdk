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

import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertOneResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler;
import com.mongodb.stitch.core.services.mongodb.remote.sync.CoreSync;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ErrorListener;
import com.mongodb.stitch.server.services.mongodb.remote.Sync;
import com.mongodb.stitch.server.services.mongodb.remote.SyncFindIterable;

import java.util.Set;

import javax.annotation.Nonnull;

import org.bson.BsonValue;
import org.bson.conversions.Bson;

/**
 * A set of synchronization related operations for a collection
 */
public class SyncImpl<DocumentT> implements Sync<DocumentT> {
  private final CoreSync<DocumentT> proxy;

  SyncImpl(final CoreSync<DocumentT> proxy) {
    this.proxy = proxy;
  }

  @Override
  public void configure(@Nonnull final ConflictHandler<DocumentT> conflictResolver,
                        final ChangeEventListener<DocumentT> changeEventListener,
                        final ErrorListener errorListener) {
    this.proxy.configure(conflictResolver, changeEventListener, errorListener);
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
    return new SyncFindIterableImpl<>(proxy.find());
  }

  @Override
  public SyncFindIterable<DocumentT> find(final Bson filter) {
    return new SyncFindIterableImpl<>(proxy.find(filter));
  }

  @Override
  public <ResultT> SyncFindIterable<ResultT> find(final Class<ResultT> resultClass) {
    return new SyncFindIterableImpl<>(proxy.find(resultClass));
  }

  @Override
  public <ResultT> SyncFindIterable<ResultT> find(final Bson filter,
                                                  final Class<ResultT> resultClass) {
    return new SyncFindIterableImpl<>(proxy.find(filter, resultClass));
  }

  @Override
  public DocumentT findOneById(final BsonValue documentId) {
    return proxy.findOneById(documentId);
  }

  @Override
  public <ResultT> ResultT findOneById(final BsonValue documentId,
                                       final Class<ResultT> resultClass) {
    return proxy.findOneById(documentId, resultClass);
  }

  @Override
  public RemoteDeleteResult deleteOneById(final BsonValue documentId) {
    return proxy.deleteOneById(documentId);
  }

  @Override
  public RemoteInsertOneResult insertOneAndSync(final DocumentT document) {
    return this.proxy.insertOneAndSync(document);
  }

  @Override
  public RemoteUpdateResult updateOneById(final BsonValue documentId, final Bson update) {
    return proxy.updateOneById(documentId, update);
  }
}
