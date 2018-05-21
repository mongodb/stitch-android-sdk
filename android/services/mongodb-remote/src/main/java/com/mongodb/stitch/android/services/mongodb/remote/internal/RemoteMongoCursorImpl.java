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
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCursor;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoCursor;
import java.io.IOException;
import java.util.concurrent.Callable;

class RemoteMongoCursorImpl<ResultT> implements RemoteMongoCursor<ResultT> {
  private final CoreRemoteMongoCursor<ResultT> proxy;
  private final TaskDispatcher dispatcher;

  RemoteMongoCursorImpl(
      final CoreRemoteMongoCursor<ResultT> cursor,
      final TaskDispatcher dispatcher
  ) {
    this.proxy = cursor;
    this.dispatcher = dispatcher;
  }

  @Override
  public Task<Boolean> hasNext() {
    return dispatcher.dispatchTask(new Callable<Boolean>() {
      @Override
      public Boolean call() {
        return proxy.hasNext();
      }
    });
  }

  @Override
  public Task<ResultT> next() {
    return dispatcher.dispatchTask(new Callable<ResultT>() {
      @Override
      public ResultT call() {
        return proxy.next();
      }
    });
  }

  @Override
  public Task<ResultT> tryNext() {
    return dispatcher.dispatchTask(new Callable<ResultT>() {
      @Override
      public ResultT call() {
        if (!proxy.hasNext()) {
          return null;
        }
        return proxy.next();
      }
    });
  }

  @Override
  public void close() throws IOException {
    proxy.close();
  }
}
