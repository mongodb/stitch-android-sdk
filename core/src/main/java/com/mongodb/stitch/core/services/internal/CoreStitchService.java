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

package com.mongodb.stitch.core.services.internal;

import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient;
import com.mongodb.stitch.core.internal.net.Method;
import com.mongodb.stitch.core.internal.net.StitchAuthDocRequest;
import java.util.List;
import org.bson.Document;
import org.bson.codecs.Codec;

import javax.annotation.Nullable;


public class CoreStitchService {
  private final StitchAuthRequestClient requestClient;
  private final StitchServiceRoutes serviceRoutes;
  private final String serviceName;

  protected CoreStitchService(
      final StitchAuthRequestClient requestClient,
      final StitchServiceRoutes routes,
      final String name) {
    this.requestClient = requestClient;
    this.serviceRoutes = routes;
    this.serviceName = name;
  }

  private StitchAuthDocRequest getCallServiceFunctionRequest(
      final String name,
      final List<? extends Object> args,
      final @Nullable Long requestTimeout) {
    final Document body = new Document();
    body.put("name", name);
    body.put("service", serviceName);
    body.put("arguments", args);

    final StitchAuthDocRequest.Builder reqBuilder = new StitchAuthDocRequest.Builder();
    reqBuilder.withMethod(Method.POST).withPath(serviceRoutes.getFunctionCallRoute());
    reqBuilder.withDocument(body);
    reqBuilder.withTimeout(requestTimeout);
    return reqBuilder.build();
  }

  public <T> T callFunctionInternal(
      final String name,
      final List<? extends Object> args,
      final @Nullable Long requestTimeout,
      final Codec<T> codec
      ) {
    return requestClient.doAuthenticatedJsonRequest(
        getCallServiceFunctionRequest(name, args, requestTimeout), codec);
  }

  public <T> T callFunctionInternal(
      final String name,
      final List<? extends Object> args,
      final @Nullable Long requestTimeout,
      final Class<T> resultClass) {
    return requestClient.doAuthenticatedJsonRequest(
        getCallServiceFunctionRequest(name, args, requestTimeout), resultClass);
  }
}
