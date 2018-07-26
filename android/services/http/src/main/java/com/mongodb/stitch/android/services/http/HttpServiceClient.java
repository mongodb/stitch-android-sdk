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

package com.mongodb.stitch.android.services.http;

import android.support.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.android.core.services.internal.NamedServiceClientFactory;
import com.mongodb.stitch.android.services.http.internal.HttpServiceClientImpl;
import com.mongodb.stitch.core.StitchAppClientInfo;
import com.mongodb.stitch.core.services.http.HttpRequest;
import com.mongodb.stitch.core.services.http.HttpResponse;
import com.mongodb.stitch.core.services.http.internal.CoreHttpServiceClient;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;

/**
 * The HTTP service client.
 */
public interface HttpServiceClient {

  /**
   * Executes the given {@link HttpRequest}.
   *
   * @param request the request to execute.
   * @return a task containing the response to executing the request.
   */
  Task<HttpResponse> execute(@NonNull final HttpRequest request);

  NamedServiceClientFactory<HttpServiceClient> factory =
      new NamedServiceClientFactory<HttpServiceClient>() {
        @Override
        public HttpServiceClient getClient(
            final CoreStitchServiceClient service,
            final StitchAppClientInfo appInfo,
            final TaskDispatcher dispatcher
        ) {
          return new HttpServiceClientImpl(new CoreHttpServiceClient(service), dispatcher);
        }
      };
}
