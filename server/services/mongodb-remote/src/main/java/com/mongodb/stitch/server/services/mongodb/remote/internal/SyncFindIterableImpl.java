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

import com.mongodb.stitch.core.services.mongodb.remote.sync.CoreSyncFindIterable;
import com.mongodb.stitch.server.services.mongodb.remote.SyncFindIterable;

import org.bson.conversions.Bson;

public class SyncFindIterableImpl<ResultT> extends RemoteMongoIterableImpl<ResultT>
    implements SyncFindIterable<ResultT> {

  private final CoreSyncFindIterable<ResultT> proxy;

  SyncFindIterableImpl(final CoreSyncFindIterable<ResultT> iterable) {
    super(iterable);
    this.proxy = iterable;
  }

  @Override
  public SyncFindIterable<ResultT> filter(final Bson filter) {
    proxy.filter(filter);
    return this;
  }

  @Override
  public SyncFindIterable<ResultT> limit(final int limit) {
    proxy.limit(limit);
    return this;
  }

  @Override
  public SyncFindIterable<ResultT> projection(final Bson projection) {
    proxy.projection(projection);
    return this;
  }

  @Override
  public SyncFindIterable<ResultT> sort(final Bson sort) {
    proxy.sort(sort);
    return this;
  }
}
