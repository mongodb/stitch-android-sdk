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

package com.mongodb.stitch.android.core;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.auth.StitchAuth;
import com.mongodb.stitch.android.services.internal.NamedServiceClientProvider;
import com.mongodb.stitch.android.services.internal.ServiceClientProvider;
import java.util.List;
import org.bson.codecs.Codec;
import org.bson.codecs.Decoder;


public interface StitchAppClient {

  /**
   * Gets the authentication component of the app. This is used for logging in and managing users.
   */
  StitchAuth getAuth();

  /**
   * Gets a client for the given named service.
   *
   * @param provider The provider that will create a client for the service.
   * @param serviceName The name of the service.
   * @param <T> The type of client to be returned by the provider.
   * @return A client to interact with the service.
   */
  <T> T getServiceClient(final NamedServiceClientProvider<T> provider, final String serviceName);

  /**
   * Gets a client for the given service. Only some services offer a provider that requires no
   * service name.
   *
   * @param provider The provider that will create a client for the service.
   * @param <T> The type of client to be returned by the provider.
   * @return A client to interact with the service.
   */
  <T> T getServiceClient(final ServiceClientProvider<T> provider);

  /**
   * Calls the specified Stitch function, and decodes the response into an instance of the specified
   * type. The response will be decoded using the codec registry specified when the client was
   * configured. If no codec registry was configured, a default codec registry will be used. The
   * default codec registry supports the mappings specified <a
   * href="http://mongodb.github.io/mongo-java-driver/3.1/bson/documents/#document">here</a>
   *
   * @param name The name of the Stitch function to call.
   * @param args The arguments to pass to the function.
   * @param resultClass The class that the response should be decoded as.
   * @param <ResultT> The type into which the Stitch response will be decoded.
   * @return A {@link Task} containing the decoded value.
   */
  <ResultT> Task<ResultT> callFunction(
      final String name, final List<? extends Object> args, final Class<ResultT> resultClass);

  /**
   * Calls the specified Stitch function, and decodes the response into an instance of the specified
   * type. The response will be decoded using the codec registry specified when the client was
   * configured. If no codec registry was configured, a default codec registry will be used. The
   * default codec registry supports the mappings specified <a
   * href="http://mongodb.github.io/mongo-java-driver/3.1/bson/documents/#document">here</a>
   * Also accepts a timeout in milliseconds. Use this for functions that may run longer than the
   * client-wide default timeout (15 seconds by default).
   *
   * @param name The name of the Stitch function to call.
   * @param args The arguments to pass to the function.
   * @param requestTimeout The number of milliseconds the client should wait for a response from the
   *                       server before failing with an error.
   * @param resultClass The class that the response should be decoded as.
   * @param <ResultT> The type into which the Stitch response will be decoded.
   * @return A {@link Task} containing the decoded value.
   */
  <ResultT> Task<ResultT> callFunction(
          final String name,
          final List<? extends Object> args,
          final Long requestTimeout,
          final Class<ResultT> resultClass);

  /**
   * Calls the specified Stitch function, and decodes the response into a value using the provided
   * {@link Decoder} or {@link Codec}.
   *
   * @param name The name of the Stitch function to call.
   * @param args The arguments to pass to the function.
   * @param resultDecoder The {@link Decoder} or {@link Codec} to use to decode the response into a
   *     value.
   * @param <ResultT> The type into which the response will be decoded.
   * @return A {@link Task} containing the decoded value.
   */
  <ResultT> Task<ResultT> callFunction(
      final String name, final List<? extends Object> args, final Decoder<ResultT> resultDecoder);

  /**
   * Calls the specified Stitch function, and decodes the response into a value using the provided
   * {@link Decoder} or {@link Codec}. Also accepts a timeout in milliseconds. Use this for
   * functions that may run longer than the client-wide default timeout (15 seconds by default).
   *
   * @param name The name of the Stitch function to call.
   * @param args The arguments to pass to the function.
   * @param requestTimeout The number of milliseconds the client should wait for a response from the
   *                       server before failing with an error.
   * @param resultDecoder The {@link Decoder} or {@link Codec} to use to decode the response into a
   *     value.
   * @param <ResultT> The type into which the response will be decoded.
   * @return A {@link Task} containing the decoded value.
   */
  <ResultT> Task<ResultT> callFunction(
          final String name,
          final List<? extends Object> args,
          final Long requestTimeout,
          final Decoder<ResultT> resultDecoder);
}
