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

package com.mongodb.stitch.core.internal;

import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient;
import com.mongodb.stitch.core.internal.net.Method;
import com.mongodb.stitch.core.internal.net.StitchAppRoutes;
import com.mongodb.stitch.core.internal.net.StitchAuthDocRequest;
import java.util.List;
import org.bson.Document;
import org.bson.codecs.Decoder;

public final class CoreStitchAppClient {
  private final StitchAuthRequestClient authRequestClient;
  private final StitchAppRoutes routes;

  public CoreStitchAppClient(
      final StitchAuthRequestClient authRequestClient, final StitchAppRoutes routes) {
    this.authRequestClient = authRequestClient;
    this.routes = routes;
  }

  private StitchAuthDocRequest getCallFunctionRequest(
      final String name, final List<? extends Object> args) {
    final Document body = new Document();
    body.put("name", name);
    body.put("arguments", args);

    final StitchAuthDocRequest.Builder reqBuilder = new StitchAuthDocRequest.Builder();
    reqBuilder.withMethod(Method.POST).withPath(routes.getFunctionCallRoute());
    reqBuilder.withDocument(body);
    return reqBuilder.build();
  }

  /**
   * Calls the specified Stitch function, and decodes the response into a value using the provided
   * {@link Decoder}.
   *
   * @param name The name of the Stitch function to call.
   * @param args The arguments to pass to the Stitch function.
   * @param decoder The {@link Decoder} to use to decode the Stitch response into a value.
   * @param <T> The type into which the Stitch response will be decoded.
   * @return The decoded value.
   */
  public <T> T callFunctionInternal(
      final String name, final List<? extends Object> args, final Decoder<T> decoder) {
    return authRequestClient.doAuthenticatedJsonRequest(
        getCallFunctionRequest(name, args), decoder);
  }

  /**
   * Calls the specified Stitch function, and decodes the response into an instance of the specified
   * type. The response will be decoded using the codec registry specified when the client was
   * configured. If no codec registry was configured, a default codec registry will be used. The
   * default codec registry supports the mappings specified <a
   * href="http://mongodb.github.io/mongo-java-driver/3.1/bson/documents/#document">here</a>
   *
   * @param name The name of the Stitch function to call.
   * @param args The arguments to pass to the Stitch function.
   * @param resultClass The class that the Stitch response should be decoded as.
   * @param <T> The type into which the Stitch response will be decoded.
   * @return The decoded value.
   */
  public <T> T callFunctionInternal(
      final String name, final List<? extends Object> args, final Class<T> resultClass) {
    return authRequestClient.doAuthenticatedJsonRequest(
        getCallFunctionRequest(name, args), resultClass);
  }
}
