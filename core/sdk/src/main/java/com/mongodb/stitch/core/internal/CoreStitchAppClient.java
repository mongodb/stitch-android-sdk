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
import com.mongodb.stitch.core.internal.net.StitchAppRoutes;
import com.mongodb.stitch.core.services.internal.CoreStitchService;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceImpl;
import java.util.List;
import javax.annotation.Nullable;
import org.bson.codecs.Decoder;
import org.bson.codecs.configuration.CodecRegistry;

public final class CoreStitchAppClient {
  private final CoreStitchService functionService;

  /**
   * Constructs a new app client.
   *
   * @param authRequestClient the request client to used for authenticated requests.
   * @param routes the app specific routes.
   * @param codecRegistry the codec registry used for de/serialization.
   */
  public CoreStitchAppClient(
      final StitchAuthRequestClient authRequestClient,
      final StitchAppRoutes routes,
      final CodecRegistry codecRegistry
  ) {
    this.functionService = new CoreStitchServiceImpl(
        authRequestClient,
        routes.getServiceRoutes(),
        codecRegistry);
  }

  /**
   * Calls the specified Stitch function, and decodes the response into a value using the provided
   * {@link Decoder}.
   *
   * @param name the name of the Stitch function to call.
   * @param args the arguments to pass to the Stitch function.
   * @param requestTimeout the number of milliseconds the client should wait for a response from the
   *                       server before failing with an error.
   * @param decoder the {@link Decoder} to use to decode the Stitch response into a value.
   * @param <T> the type into which the Stitch response will be decoded.
   * @return the decoded value.
   */
  public <T> T callFunctionInternal(
      final String name,
      final List<?> args,
      final @Nullable Long requestTimeout,
      final Decoder<T> decoder) {
    return this.functionService.callFunctionInternal(name, args, requestTimeout, decoder);
  }

  /**
   * Calls the specified Stitch function, and decodes the response into an instance of the specified
   * type. The response will be decoded using the codec registry specified when the client was
   * configured. If no codec registry was configured, a default codec registry will be used. The
   * default codec registry supports the mappings specified <a
   * href="http://mongodb.github.io/mongo-java-driver/3.1/bson/documents/#document">here</a>
   *
   * @param name the name of the Stitch function to call.
   * @param args the arguments to pass to the Stitch function.
   * @param requestTimeout the number of milliseconds the client should wait for a response from the
   *                       server before failing with an error.
   * @param resultClass the class that the Stitch response should be decoded as.
   * @param <T> the type into which the Stitch response will be decoded.
   * @return the decoded value.
   */
  public <T> T callFunctionInternal(
      final String name,
      final List<?> args,
      final @Nullable Long requestTimeout,
      final Class<T> resultClass) {
    return this.functionService.callFunctionInternal(name, args, requestTimeout, resultClass);
  }

  /**
  * Calls the specified Stitch function, and decodes the response into an instance of the specified
  * type. The response will be decoded using the codec registry given.
  *
  * @param name the name of the Stitch function to call.
  * @param args the arguments to pass to the Stitch function.
  * @param requestTimeout the number of milliseconds the client should wait for a response from the
  *                       server before failing with an error.
  * @param resultClass the class that the Stitch response should be decoded as.
  * @param <T> the type into which the Stitch response will be decoded.
  * @param codecRegistry the codec registry that will be used to encode/decode the function call.
  * @return the decoded value.
  */
  public <T> T callFunctionInternal(
      final String name,
      final List<?> args,
      final @Nullable Long requestTimeout,
      final Class<T> resultClass,
      final CodecRegistry codecRegistry
  ) {
    return this.functionService
        .withCodecRegistry(codecRegistry)
        .callFunctionInternal(name, args, requestTimeout, resultClass);
  }
}
