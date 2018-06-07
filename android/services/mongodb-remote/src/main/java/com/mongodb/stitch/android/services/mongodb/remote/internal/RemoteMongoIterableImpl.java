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
import com.mongodb.Block;
import com.mongodb.Function;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCursor;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoIterable;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoIterable;
import java.util.Collection;
import java.util.concurrent.Callable;

public class RemoteMongoIterableImpl<ResultT> implements RemoteMongoIterable<ResultT> {

  private final CoreRemoteMongoIterable<ResultT> proxy;
  private final TaskDispatcher dispatcher;

  RemoteMongoIterableImpl(
      final CoreRemoteMongoIterable<ResultT> iterable,
      final TaskDispatcher dispatcher
  ) {
    this.proxy = iterable;
    this.dispatcher = dispatcher;
  }

  @NonNull
  public Task<RemoteMongoCursor<ResultT>> iterator() {
    return dispatcher.dispatchTask(new Callable<RemoteMongoCursor<ResultT>>() {
      @Override
      public RemoteMongoCursor<ResultT> call() {
        return new RemoteMongoCursorImpl<>(proxy.iterator(), dispatcher);
        }
    });
  }

  @NonNull
  @Override
  public Task<ResultT> first() {
    return dispatcher.dispatchTask(new Callable<ResultT>() {
      @Override
      public ResultT call() {
        return proxy.first();
      }
    });
  }

  @Override
  public <U> RemoteMongoIterable<U> map(final Function<ResultT, U> mapper) {
    return new RemoteMongoIterableImpl<>(proxy.map(mapper), dispatcher);
  }

  @Override
  public Task<Void> forEach(final Block<? super ResultT> block) {
    return dispatcher.dispatchTask(new Callable<Void>() {
      @Override
      public Void call() {
        proxy.forEach(block);
        return null;
      }
    });
  }

  @Override
  public <A extends Collection<? super ResultT>> Task<A> into(final A target) {
    return dispatcher.dispatchTask(new Callable<A>() {
      @Override
      public A call() {
        return proxy.into(target);
      }
    });
  }
}
