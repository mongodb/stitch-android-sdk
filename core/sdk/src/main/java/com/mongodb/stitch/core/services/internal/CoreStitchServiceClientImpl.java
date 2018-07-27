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

import static com.mongodb.stitch.core.internal.common.Assertions.notNull;

import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient;
import com.mongodb.stitch.core.internal.net.Method;
import com.mongodb.stitch.core.internal.net.StitchAuthDocRequest;
import java.util.List;
import javax.annotation.Nullable;
import org.bson.Document;
import org.bson.codecs.Decoder;
import org.bson.codecs.configuration.CodecRegistry;

public class CoreStitchServiceClientImpl implements CoreStitchServiceClient {
  private final StitchAuthRequestClient requestClient;
  private final StitchServiceRoutes serviceRoutes;
  private final String serviceName;
  private final CodecRegistry codecRegistry;

  public CoreStitchServiceClientImpl(
      final StitchAuthRequestClient requestClient,
      final StitchServiceRoutes routes,
      final CodecRegistry codecRegistry
  ) {
    this(requestClient, routes, "", codecRegistry);
  }

  public CoreStitchServiceClientImpl(
      final StitchAuthRequestClient requestClient,
      final StitchServiceRoutes routes,
      final String name,
      final CodecRegistry codecRegistry
  ) {
    notNull("codecRegistry", codecRegistry);
    this.requestClient = requestClient;
    this.serviceRoutes = routes;
    this.serviceName = name;
    this.codecRegistry = codecRegistry;
  }

  private StitchAuthDocRequest getCallServiceFunctionRequest(
      final String name,
      final List<?> args,
      final @Nullable Long requestTimeout) {
    final Document body = new Document();
    body.put("name", name);
    if (serviceName != null) {
      body.put("service", serviceName);
    }
    body.put("arguments", args);

    final StitchAuthDocRequest.Builder reqBuilder = new StitchAuthDocRequest.Builder();
    reqBuilder.withMethod(Method.POST).withPath(serviceRoutes.getFunctionCallRoute());
    reqBuilder.withDocument(body);
    reqBuilder.withTimeout(requestTimeout);
    return reqBuilder.build(codecRegistry);
  }

  public void callFunction(
      final String name,
      final List<?> args
  ) {
    requestClient.doAuthenticatedRequest(getCallServiceFunctionRequest(name, args, null));
  }

  public <T> T callFunction(
      final String name,
      final List<?> args,
      final Decoder<T> resultDecoder) {
    return requestClient.doAuthenticatedRequest(
        getCallServiceFunctionRequest(name, args, null), resultDecoder);
  }

  public <T> T callFunction(
      final String name,
      final List<?> args,
      final Class<T> resultClass) {
    return requestClient.doAuthenticatedRequest(
        getCallServiceFunctionRequest(name, args, null), resultClass, codecRegistry);
  }

  public <T> T callFunction(
      final String name,
      final List<?> args,
      final Class<T> resultClass,
      final CodecRegistry codecRegistry) {
    return requestClient.doAuthenticatedRequest(
        getCallServiceFunctionRequest(name, args, null), resultClass, codecRegistry);
  }

  public void callFunction(
      final String name,
      final List<?> args,
      final @Nullable Long requestTimeout
  ) {
    requestClient.doAuthenticatedRequest(getCallServiceFunctionRequest(name, args, requestTimeout));
  }

  public <T> T callFunction(
      final String name,
      final List<?> args,
      final @Nullable Long requestTimeout,
      final Decoder<T> resultDecoder) {
    return requestClient.doAuthenticatedRequest(
        getCallServiceFunctionRequest(name, args, requestTimeout), resultDecoder);
  }

  public <T> T callFunction(
      final String name,
      final List<?> args,
      final @Nullable Long requestTimeout,
      final Class<T> resultClass) {
    return requestClient.doAuthenticatedRequest(
        getCallServiceFunctionRequest(name, args, requestTimeout), resultClass, codecRegistry);
  }

  public <T> T callFunction(
      final String name,
      final List<?> args,
      final @Nullable Long requestTimeout,
      final Class<T> resultClass,
      final CodecRegistry codecRegistry) {
    return requestClient.doAuthenticatedRequest(
        getCallServiceFunctionRequest(name, args, requestTimeout), resultClass, codecRegistry);
  }

  public CodecRegistry getCodecRegistry() {
    return codecRegistry;
  }

  public CoreStitchServiceClient withCodecRegistry(final CodecRegistry codecRegistry) {
    return new CoreStitchServiceClientImpl(
        requestClient,
        serviceRoutes,
        serviceName,
        codecRegistry);
  }
}
