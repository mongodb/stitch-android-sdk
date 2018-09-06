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
import com.mongodb.stitch.android.services.mongodb.remote.RemoteFindIterable;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteFindIterable;
import org.bson.conversions.Bson;

public class RemoteFindIterableImpl<ResultT>
    extends RemoteMongoIterableImpl<ResultT>
    implements RemoteFindIterable<ResultT> {

  private final CoreRemoteFindIterable<ResultT> proxy;

  public RemoteFindIterableImpl(
      final CoreRemoteFindIterable<ResultT> iterable,
      final TaskDispatcher dispatcher
  ) {
    super(iterable, dispatcher);
    this.proxy = iterable;
  }

  @Override
  public RemoteFindIterable<ResultT> filter(@Nullable final Bson filter) {
    proxy.filter(filter);
    return this;
  }

  @Override
  public RemoteFindIterable<ResultT> limit(final int limit) {
    proxy.limit(limit);
    return this;
  }

  @Override
  public RemoteFindIterable<ResultT> projection(@Nullable final Bson projection) {
    proxy.projection(projection);
    return this;
  }

  @Override
  public RemoteFindIterable<ResultT> sort(@Nullable final Bson sort) {
    proxy.sort(sort);
    return this;
  }
}
