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
import com.mongodb.stitch.android.core.services.internal.NamedServiceClientFactory;
import com.mongodb.stitch.android.core.services.internal.ServiceClientFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import org.bson.codecs.Codec;
import org.bson.codecs.Decoder;
import org.bson.codecs.configuration.CodecRegistry;


public interface StitchAppClient extends Closeable {

  /**
   * Gets the authentication component of the app. This is used for logging in and managing users.
   */
  StitchAuth getAuth();

  /**
   * Gets a client for the given named service.
   *
   * @param provider the provider that will create a client for the service.
   * @param serviceName the name of the service.
   * @param <T> the type of client to be returned by the provider.
   * @return a client to interact with the service.
   */
  <T> T getServiceClient(final NamedServiceClientFactory<T> provider, final String serviceName);

  /**
   * Gets a client for the given service. Only some services offer a provider that requires no
   * service name.
   *
   * @param provider the provider that will create a client for the service.
   * @param <T> the type of client to be returned by the provider.
   * @return a client to interact with the service.
   */
  <T> T getServiceClient(final ServiceClientFactory<T> provider);

  /**
   * Calls the specified Stitch function, and decodes the response into an instance of the specified
   * type. The response will be decoded using the codec registry specified when the client was
   * configured. If no codec registry was configured, a default codec registry will be used. The
   * default codec registry supports the mappings specified <a
   * href="http://mongodb.github.io/mongo-java-driver/3.1/bson/documents/#document">here</a>
   *
   * @param name he name of the Stitch function to call.
   * @param args he arguments to pass to the function.
   * @param resultClass he class that the response should be decoded as.
   * @param <ResultT> he type into which the Stitch response will be decoded.
   * @return a {@link Task} containing the decoded value.
   */
  <ResultT> Task<ResultT> callFunction(
      final String name, final List<?> args, final Class<ResultT> resultClass);

  /**
   * Calls the specified Stitch function, and decodes the response into an instance of the specified
   * type. The response will be decoded using the codec registry specified when the client was
   * configured. If no codec registry was configured, a default codec registry will be used. The
   * default codec registry supports the mappings specified <a
   * href="http://mongodb.github.io/mongo-java-driver/3.1/bson/documents/#document">here</a>
   * Also accepts a timeout in milliseconds. Use this for functions that may run longer than the
   * client-wide default timeout (15 seconds by default).
   *
   * @param name the name of the Stitch function to call.
   * @param args the arguments to pass to the function.
   * @param requestTimeout the number of milliseconds the client should wait for a response from the
   *                       server before failing with an error.
   * @param resultClass the class that the response should be decoded as.
   * @param <ResultT> the type into which the Stitch response will be decoded.
   * @return a {@link Task} containing the decoded value.
   */
  <ResultT> Task<ResultT> callFunction(
          final String name,
          final List<?> args,
          final Long requestTimeout,
          final Class<ResultT> resultClass);

  /**
   * Calls the specified Stitch function, and decodes the response into a value using the provided
   * {@link Decoder} or {@link Codec}.
   *
   * @param name the name of the Stitch function to call.
   * @param args the arguments to pass to the function.
   * @param resultDecoder the {@link Decoder} or {@link Codec} to use to decode the response into a
   *     value.
   * @param <ResultT> the type into which the response will be decoded.
   * @return a {@link Task} containing the decoded value.
   */
  <ResultT> Task<ResultT> callFunction(
      final String name, final List<?> args, final Decoder<ResultT> resultDecoder);

  /**
   * Calls the specified Stitch function, and decodes the response into a value using the provided
   * {@link Decoder} or {@link Codec}. Also accepts a timeout in milliseconds. Use this for
   * functions that may run longer than the client-wide default timeout (15 seconds by default).
   *
   * @param name the name of the Stitch function to call.
   * @param args the arguments to pass to the function.
   * @param requestTimeout the number of milliseconds the client should wait for a response from the
   *                       server before failing with an error.
   * @param resultDecoder the {@link Decoder} or {@link Codec} to use to decode the response into a
   *     value.
   * @param <ResultT> the type into which the response will be decoded.
   * @return a {@link Task} containing the decoded value.
   */
  <ResultT> Task<ResultT> callFunction(
          final String name,
          final List<?> args,
          final Long requestTimeout,
          final Decoder<ResultT> resultDecoder);

  /**
   * Calls the specified Stitch function, and decodes the response into an instance of the specified
   * type. The response will be decoded using the codec registry given.
   *
   * @param name the name of the Stitch function to call.
   * @param args the arguments to pass to the function.
   * @param resultClass the class that the response should be decoded as.
   * @param codecRegistry the codec registry used for de/serialization of the function call.
   * @param <ResultT> the type into which the Stitch response will be decoded.
   * @return a {@link Task} containing the decoded value.
   */
  <ResultT> Task<ResultT> callFunction(
      final String name,
      final List<?> args,
      final Class<ResultT> resultClass,
      final CodecRegistry codecRegistry);

  /**
   * Calls the specified Stitch function, and decodes the response into an instance of the specified
   * type. The response will be decoded using the codec registry given.
   * Also accepts a timeout in milliseconds. Use this for functions that may run longer than the
   * client-wide default timeout (15 seconds by default).
   *
   * @param name the name of the Stitch function to call.
   * @param args the arguments to pass to the function.
   * @param requestTimeout the number of milliseconds the client should wait for a response from the
   *                       server before failing with an error.
   * @param resultClass the class that the response should be decoded as.
   * @param codecRegistry the codec registry used for de/serialization of the function call.
   * @param <ResultT> the type into which the Stitch response will be decoded.
   * @return a {@link Task} containing the decoded value.
   */
  <ResultT> Task<ResultT> callFunction(
      final String name,
      final List<?> args,
      final Long requestTimeout,
      final Class<ResultT> resultClass,
      final CodecRegistry codecRegistry);

  /**
   * Closes the client and shuts down all background operations.
   */
  void close() throws IOException;
}
