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

package com.mongodb.stitch.android.services.http.internal;

import android.support.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.android.core.services.internal.StitchService;
import com.mongodb.stitch.android.services.http.HttpServiceClient;
import com.mongodb.stitch.core.services.http.HttpRequest;
import com.mongodb.stitch.core.services.http.HttpResponse;
import com.mongodb.stitch.core.services.http.internal.CoreHttpServiceClient;
import java.util.concurrent.Callable;

public final class HttpServiceClientImpl extends CoreHttpServiceClient
    implements HttpServiceClient {

  private final TaskDispatcher dispatcher;

  public HttpServiceClientImpl(final StitchService service, final TaskDispatcher dispatcher) {
    super(service);
    this.dispatcher = dispatcher;
  }


  /**
   * Executes the given {@link HttpRequest}.
   *
   * @param request the request to execute.
   * @return a task containing the response to executing the request.
   */
  public Task<HttpResponse> execute(@NonNull final HttpRequest request) {
    return dispatcher.dispatchTask(new Callable<HttpResponse>() {
      @Override
      public HttpResponse call() {
        return executeInternal(request);
      }
    });
  }
}
