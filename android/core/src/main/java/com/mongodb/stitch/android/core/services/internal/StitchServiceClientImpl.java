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
import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClientImpl;
import com.mongodb.stitch.core.services.internal.StitchServiceRoutes;
import java.util.List;
import java.util.concurrent.Callable;
import org.bson.codecs.Decoder;
import org.bson.codecs.configuration.CodecRegistry;

public final class StitchServiceClientImpl
    extends CoreStitchServiceClientImpl implements StitchServiceClient {
  private final TaskDispatcher dispatcher;

  public StitchServiceClientImpl(
      final StitchAuthRequestClient requestClient,
      final StitchServiceRoutes routes,
      final String name,
      final CodecRegistry codecRegistry,
      final TaskDispatcher dispatcher) {
    super(requestClient, routes, name, codecRegistry);
    this.dispatcher = dispatcher;
  }

  @Override
  public <ResultT> Task<ResultT> callFunction(
      final String name, final List<?> args, final Class<ResultT> resultClass) {
    return dispatcher.dispatchTask(
        new Callable<ResultT>() {
          @Override
          public ResultT call() {
            return callFunctionInternal(name, args, null, resultClass);
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
            return callFunctionInternal(name, args, requestTimeout, resultClass);
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
            return callFunctionInternal(name, args, null, resultDecoder);
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
            return callFunctionInternal(name, args, requestTimeout, resultDecoder);
          }
        });
  }
}