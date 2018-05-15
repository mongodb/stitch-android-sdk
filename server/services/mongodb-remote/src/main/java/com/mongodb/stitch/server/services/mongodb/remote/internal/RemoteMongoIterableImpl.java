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

import com.mongodb.Block;
import com.mongodb.Function;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoIterable;
import com.mongodb.stitch.server.services.mongodb.remote.RemoteMongoCursor;
import com.mongodb.stitch.server.services.mongodb.remote.RemoteMongoIterable;

import java.util.Collection;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class RemoteMongoIterableImpl<ResultT> implements RemoteMongoIterable<ResultT> {

  private final CoreRemoteMongoIterable<ResultT> proxy;

  RemoteMongoIterableImpl(final CoreRemoteMongoIterable<ResultT> iterable) {
    this.proxy = iterable;
  }

  @Override
  @Nonnull
  public RemoteMongoCursor<ResultT> iterator() {
    return new RemoteMongoCursorImpl<>(proxy.iterator());
  }

  @Nullable
  @Override
  public ResultT first() {
    return proxy.first();
  }

  @Override
  public <U> RemoteMongoIterable<U> map(final Function<ResultT, U> mapper) {
    return new RemoteMongoIterableImpl<>(proxy.map(mapper));
  }

  @Override
  public void forEach(final Block<? super ResultT> block) {
    proxy.forEach(block);
  }

  @Override
  public <A extends Collection<? super ResultT>> A into(final A target) {
    return proxy.into(target);
  }
}
