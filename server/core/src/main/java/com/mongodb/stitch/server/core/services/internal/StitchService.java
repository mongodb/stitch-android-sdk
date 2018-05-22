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

package com.mongodb.stitch.server.core.services.internal;

import com.mongodb.stitch.core.services.internal.CoreStitchService;

import java.util.List;
import org.bson.codecs.Decoder;

public interface StitchService extends CoreStitchService {
  /**
   * Calls the specified Stitch service function, and decodes the response into an instance of the
   * specified type. The response will be decoded using the codec registry specified when the app
   * client was configured. If no codec registry was configured, a default codec registry will be
   * used. The default codec registry supports the mappings specified <a
   * href="http://mongodb.github.io/mongo-java-driver/3.1/bson/documents/#document">here</a>
   *
   * @param name the name of the Stitch service function to call.
   * @param args the arguments to pass to the function.
   * @param resultClass the class that the response should be decoded as.
   * @param <ResultT> the type into which the response will be decoded.
   * @return the decoded value.
   */
  <ResultT> ResultT callFunction(
      final String name, final List<?> args, final Class<ResultT> resultClass);

  /**
   * Calls the specified Stitch service function, and decodes the response into an instance of the
   * specified type. The response will be decoded using the codec registry specified when the app
   * client was configured. If no codec registry was configured, a default codec registry will be
   * used. The default codec registry supports the mappings specified <a
   * href="http://mongodb.github.io/mongo-java-driver/3.1/bson/documents/#document">here</a>
   * Also accepts a timeout in milliseconds. Use this for functions that may run longer than the
   * client-wide default timeout (15 seconds by default).
   *
   * @param name the name of the Stitch service function to call.
   * @param args the arguments to pass to the function.
   * @param requestTimeout the number of milliseconds the client should wait for a response from the
   *                       server before failing with an error.
   * @param resultClass the class that the response should be decoded as.
   * @param <ResultT> the type into which the response will be decoded.
   * @return the decoded value.
   */
  <ResultT> ResultT callFunction(
          final String name,
          final List<?> args,
          final Long requestTimeout,
          final Class<ResultT> resultClass);

  /**
   * Calls the specified Stitch service function, and decodes the response into a value using the
   * provided {@link Decoder}.
   *
   * @param name the name of the Stitch service function to call.
   * @param args the arguments to pass to the function.
   * @param resultDecoder the {@link Decoder} to use to decode the response into a value.
   * @param <ResultT> the type into which the response will be decoded.
   * @return the decoded value.
   */
  <ResultT> ResultT callFunction(
      final String name, final List<?> args, final Decoder<ResultT> resultDecoder);

  /**
   * Calls the specified Stitch service function, and decodes the response into a value using the
   * provided {@link Decoder}. Also accepts a timeout in milliseconds. Use this for functions that
   * may run longer than the client-wide default timeout (15 seconds by default).
   *
   * @param name the name of the Stitch service function to call.
   * @param args the arguments to pass to the function.
   * @param requestTimeout the number of milliseconds the client should wait for a response from the
   *                       server before failing with an error.
   * @param resultDecoder the {@link Decoder} to use to decode the response into a value.
   * @param <ResultT> the type into which the response will be decoded.
   * @return the decoded value.
   */
  <ResultT> ResultT callFunction(
          final String name,
          final List<?> args,
          final Long requestTimeout,
          final Decoder<ResultT> resultDecoder);
}
