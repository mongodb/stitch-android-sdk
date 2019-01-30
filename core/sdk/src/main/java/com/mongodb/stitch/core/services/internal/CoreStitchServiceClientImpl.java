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
import com.mongodb.stitch.core.internal.net.StitchAuthRequest;
import com.mongodb.stitch.core.internal.net.Stream;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import org.bson.Document;
import org.bson.codecs.Decoder;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.internal.Base64;

public class CoreStitchServiceClientImpl implements CoreStitchServiceClient {
  private final StitchAuthRequestClient requestClient;
  private final StitchServiceRoutes serviceRoutes;
  private final String serviceName;
  private final CodecRegistry codecRegistry;
  private final List<WeakReference<StitchServiceBinder>> serviceBinders;

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
    this.serviceBinders = new ArrayList<>();
  }

  private StitchAuthRequest getStreamServiceFunctionRequest(
      final String name,
      final List<?> args) {
    final Document body = new Document();
    body.put(FunctionFields.NAME, name);
    if (serviceName != null) {
      body.put(FunctionFields.SERVICE, serviceName);
    }
    body.put(FunctionFields.ARGUMENTS, args);

    final StitchAuthRequest.Builder reqBuilder = new StitchAuthRequest.Builder();
    reqBuilder.withMethod(Method.GET).withPath(serviceRoutes.getFunctionCallRoute()
        + (FunctionFields.STITCH_REQUEST
        + Base64.encode(body.toJson().getBytes(StandardCharsets.UTF_8))));
    return reqBuilder.build();
  }

  private StitchAuthDocRequest getCallServiceFunctionRequest(
      final String name,
      final List<?> args,
      final @Nullable Long requestTimeout) {
    final Document body = new Document();
    body.put(FunctionFields.NAME, name);
    if (serviceName != null) {
      body.put(FunctionFields.SERVICE, serviceName);
    }
    body.put(FunctionFields.ARGUMENTS, args);

    final StitchAuthDocRequest.Builder reqBuilder = new StitchAuthDocRequest.Builder();
    reqBuilder.withMethod(Method.POST).withPath(serviceRoutes.getFunctionCallRoute());
    reqBuilder.withDocument(body);
    reqBuilder.withTimeout(requestTimeout);
    return reqBuilder.build(codecRegistry);
  }

  @Nullable
  public String getName() {
    return serviceName;
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

  @Override
  public <T> Stream<T> streamFunction(final String name,
                                      final List<?> args,
                                      final Decoder<T> decoder) {
    return requestClient.openAuthenticatedStream(
        getStreamServiceFunctionRequest(name, args), decoder
    );
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

  @Override
  public void bind(StitchServiceBinder binder) {
    this.serviceBinders.add(new WeakReference<>(binder));
  }

  @Override
  public void onRebindEvent() {
    final Iterator<WeakReference<StitchServiceBinder>> iterator = this.serviceBinders.iterator();
    while (iterator.hasNext()) {
      final WeakReference<StitchServiceBinder> weakReference = iterator.next();
      final StitchServiceBinder binder = weakReference.get();
      if (binder == null) {
        this.serviceBinders.remove(weakReference);
      } else {
        binder.onRebindEvent();
      }
    }
  }

  private static class FunctionFields {
    private static final String NAME = "name";
    private static final String SERVICE = "service";
    private static final String ARGUMENTS = "arguments";

    private static final String STITCH_REQUEST = "?stitch_request=";
  }
}
