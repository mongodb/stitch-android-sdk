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

import android.support.annotation.Nullable;

import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.android.services.mongodb.remote.SyncFindIterable;
import com.mongodb.stitch.core.services.mongodb.remote.sync.CoreSyncFindIterable;

import org.bson.conversions.Bson;

/**
 * Iterable for find in the context of sync.
 *
 * @param <ResultT> The type of the result.
 */
public class SyncFindIterableImpl<ResultT> extends RemoteMongoIterableImpl<ResultT>
    implements SyncFindIterable<ResultT> {

  private final CoreSyncFindIterable<ResultT> proxy;

  SyncFindIterableImpl(final CoreSyncFindIterable<ResultT> iterable,
                       final TaskDispatcher dispatcher) {
    super(iterable, dispatcher);
    this.proxy = iterable;
  }

  @Override
  public SyncFindIterable<ResultT> filter(@Nullable final Bson filter) {
    proxy.filter(filter);
    return this;
  }

  @Override
  public SyncFindIterable<ResultT> limit(final int limit) {
    proxy.limit(limit);
    return this;
  }

  @Override
  public SyncFindIterable<ResultT> projection(@Nullable final Bson projection) {
    proxy.projection(projection);
    return this;
  }

  @Override
  public SyncFindIterable<ResultT> sort(@Nullable final Bson sort) {
    proxy.sort(sort);
    return this;
  }
}
