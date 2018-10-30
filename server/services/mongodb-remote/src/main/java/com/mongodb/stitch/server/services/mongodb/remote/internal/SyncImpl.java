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

import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler;
import com.mongodb.stitch.core.services.mongodb.remote.sync.CoreSync;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ErrorListener;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncCountOptions;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncDeleteResult;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncInsertManyResult;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncInsertOneResult;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncUpdateOptions;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncUpdateResult;
import com.mongodb.stitch.server.services.mongodb.remote.Sync;
import com.mongodb.stitch.server.services.mongodb.remote.SyncAggregateIterable;
import com.mongodb.stitch.server.services.mongodb.remote.SyncFindIterable;

import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bson.BsonDocument;
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
                        @Nullable final ChangeEventListener<DocumentT> changeEventListener,
                        @Nullable final ErrorListener errorListener) {
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
  public Set<BsonValue> getPausedDocumentIds() {
    return this.proxy.getPausedDocumentIds();
  }

  @Override
  public boolean resumeSyncForDocument(@Nonnull final BsonValue documentId) {
    return this.proxy.resumeSyncForDocument(documentId);
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
  public long count() {
    return this.count(new BsonDocument());
  }

  @Override
  public long count(Bson filter) {
    return this.count(filter, new SyncCountOptions());
  }

  @Override
  public long count(Bson filter, SyncCountOptions options) {
    return this.proxy.count(filter, options);
  }

  @Override
  public SyncAggregateIterable<DocumentT> aggregate(List<? extends Bson> pipeline) {
    return new SyncAggregateIterableImpl<>(this.proxy.aggregate(pipeline));
  }

  @Override
  public <ResultT> SyncAggregateIterable<ResultT> aggregate(List<? extends Bson> pipeline,
                                                            Class<ResultT> resultClass) {
    return new SyncAggregateIterableImpl<>(this.proxy.aggregate(pipeline, resultClass));
  }

  @Override
  public SyncInsertOneResult insertOneAndSync(DocumentT document) {
    return proxy.insertOneAndSync(document);
  }

  @Override
  public SyncInsertManyResult insertManyAndSync(List<DocumentT> documents) {
    return proxy.insertManyAndSync(documents);
  }

  @Override
  public SyncUpdateResult updateOne(Bson filter, Bson update) {
    return proxy.updateOne(filter, update);
  }

  @Override
  public SyncUpdateResult updateOne(Bson filter, Bson update, SyncUpdateOptions updateOptions) {
    return proxy.updateOne(filter, update, updateOptions);
  }

  @Override
  public SyncUpdateResult updateMany(Bson filter, Bson update) {
    return proxy.updateMany(filter, update);
  }

  @Override
  public SyncUpdateResult updateMany(Bson filter, Bson update, SyncUpdateOptions updateOptions) {
    return proxy.updateMany(filter, update, updateOptions);
  }

  @Override
  public SyncDeleteResult deleteOne(Bson filter) {
    return proxy.deleteOne(filter);
  }

  @Override
  public SyncDeleteResult deleteMany(Bson filter) {
    return proxy.deleteMany(filter);
  }
}
