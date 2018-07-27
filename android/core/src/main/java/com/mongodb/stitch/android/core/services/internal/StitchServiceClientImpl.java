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

package com.mongodb.stitch.android.core.services.internal;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.android.core.services.StitchServiceClient;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import java.util.List;
import java.util.concurrent.Callable;
import org.bson.codecs.Decoder;
import org.bson.codecs.configuration.CodecRegistry;

public final class StitchServiceClientImpl implements StitchServiceClient {
  private final CoreStitchServiceClient proxy;
  private final TaskDispatcher dispatcher;

  public StitchServiceClientImpl(
      final CoreStitchServiceClient proxy,
      final TaskDispatcher dispatcher
  ) {
    this.proxy = proxy;
    this.dispatcher = dispatcher;
  }

  @Override
  public Task<Void> callFunction(
      final String name, final List<?> args) {
    return dispatcher.dispatchTask(
        new Callable<Void>() {
          @Override
          public Void call() {
            proxy.callFunction(name, args);
            return null;
          }
        });
  }

  @Override
  public <ResultT> Task<ResultT> callFunction(
      final String name, final List<?> args, final Decoder<ResultT> resultDecoder) {
    return dispatcher.dispatchTask(
        new Callable<ResultT>() {
          @Override
          public ResultT call() {
            return proxy.callFunction(name, args, null, resultDecoder);
          }
        });
  }

  @Override
  public <ResultT> Task<ResultT> callFunction(
      final String name, final List<?> args, final Class<ResultT> resultClass) {
    return dispatcher.dispatchTask(
        new Callable<ResultT>() {
          @Override
          public ResultT call() {
            return proxy.callFunction(name, args, null, resultClass);
          }
        });
  }

  @Override
  public <ResultT> Task<ResultT> callFunction(
      final String name,
      final List<?> args,
      final Class<ResultT> resultClass,
      final CodecRegistry codecRegistry) {
    return dispatcher.dispatchTask(
        new Callable<ResultT>() {
          @Override
          public ResultT call() {
            return proxy.callFunction(name, args, null, resultClass, codecRegistry);
          }
        });
  }

  @Override
  public Task<Void> callFunction(
      final String name,
      final List<?> args,
      final Long requestTimeout) {
    return dispatcher.dispatchTask(
        new Callable<Void>() {
          @Override
          public Void call() {
            proxy.callFunction(name, args, requestTimeout);
            return null;
          }
        });
  }

  @Override
  public <ResultT> Task<ResultT> callFunction(
      final String name,
      final List<?> args,
      final Long requestTimeout,
      final Class<ResultT> resultClass) {
    return dispatcher.dispatchTask(
        new Callable<ResultT>() {
          @Override
          public ResultT call() {
            return proxy.callFunction(name, args, requestTimeout, resultClass);
          }
        });
  }

  @Override
  public <ResultT> Task<ResultT> callFunction(
      final String name,
      final List<?> args,
      final Long requestTimeout,
      final Decoder<ResultT> resultDecoder) {
    return dispatcher.dispatchTask(
        new Callable<ResultT>() {
          @Override
          public ResultT call() {
            return proxy.callFunction(name, args, requestTimeout, resultDecoder);
          }
        });
  }

  @Override
  public <ResultT> Task<ResultT> callFunction(
      final String name,
      final List<?> args,
      final Long requestTimeout,
      final Class<ResultT> resultClass,
      final CodecRegistry codecRegistry) {
    return dispatcher.dispatchTask(
        new Callable<ResultT>() {
          @Override
          public ResultT call() {
            return proxy.callFunction(
                name,
                args,
                requestTimeout,
                resultClass,
                codecRegistry);
          }
        });
  }

  @Override
  public CodecRegistry getCodecRegistry() {
    return proxy.getCodecRegistry();
  }

  @Override
  public StitchServiceClient withCodecRegistry(final CodecRegistry codecRegistry) {
    return new StitchServiceClientImpl(proxy.withCodecRegistry(codecRegistry), dispatcher);
  }
}
