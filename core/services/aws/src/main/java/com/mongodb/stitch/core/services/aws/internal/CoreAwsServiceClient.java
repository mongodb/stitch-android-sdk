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

package com.mongodb.stitch.core.services.aws.internal;

import com.mongodb.stitch.core.services.aws.AwsRequest;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import java.util.Collections;
import org.bson.Document;
import org.bson.codecs.Decoder;
import org.bson.codecs.configuration.CodecRegistry;

public class CoreAwsServiceClient {

  private final CoreStitchServiceClient service;

  public CoreAwsServiceClient(final CoreStitchServiceClient service) {
    this.service = service;
  }

  /**
   * Executes the AWS request.
   *
   * @param request the AWS request to execute.
   */
  public void execute(final AwsRequest request) {
    service.callFunction(
        Fields.EXECUTE_ACTION_NAME,
        Collections.singletonList(getRequestArgs(request)));
  }

  /**
   * Executes the AWS request, and decodes the result into an instance of the
   * specified type. The response will be decoded using the codec registry specified when the app
   * client was configured. If no codec registry was configured, a default codec registry will be
   * used. The default codec registry supports the mappings specified <a
   * href="http://mongodb.github.io/mongo-java-driver/3.1/bson/documents/#document">here</a>
   *
   * @param request the AWS request to execute.
   * @param resultDecoder the {@link Decoder} to use to decode the result into a value.
   * @param <ResultT> the type into which the response will be decoded.
   * @return the decoded result value.
   */
  public <ResultT> ResultT execute(final AwsRequest request, final Decoder<ResultT> resultDecoder) {
    return service.callFunction(
        Fields.EXECUTE_ACTION_NAME,
        Collections.singletonList(getRequestArgs(request)),
        resultDecoder);
  }

  /**
   * Executes the AWS request, and decodes the result into an instance of the
   * specified type. The response will be decoded using the codec registry specified when the app
   * client was configured. If no codec registry was configured, a default codec registry will be
   * used. The default codec registry supports the mappings specified <a
   * href="http://mongodb.github.io/mongo-java-driver/3.1/bson/documents/#document">here</a>
   *
   * @param request the AWS request to execute.
   * @param resultClass the class that the result should be decoded as.
   * @param <ResultT> the type into which the response will be decoded.
   * @return the decoded result value.
   */
  public <ResultT> ResultT execute(final AwsRequest request, final Class<ResultT> resultClass) {
    return service.callFunction(
        Fields.EXECUTE_ACTION_NAME,
        Collections.singletonList(getRequestArgs(request)),
        resultClass);
  }

  /**
   * Executes the AWS request, and decodes the result into an instance of the
   * specified type. The response will be decoded using the codec registry given.
   *
   * @param request the AWS request to execute.
   * @param resultClass the class that the result should be decoded as.
   * @param codecRegistry the codec registry used for de/serialization of the function call.
   * @param <ResultT> the type into which the response will be decoded.
   * @return the decoded result value.
   */
  public <ResultT> ResultT execute(
      final AwsRequest request,
      final Class<ResultT> resultClass,
      final CodecRegistry codecRegistry
  ) {
    return service.callFunction(
        Fields.EXECUTE_ACTION_NAME,
        Collections.singletonList(getRequestArgs(request)),
        resultClass,
        codecRegistry);
  }

  /**
   * Executes the AWS request.
   *
   * @param request the AWS request to execute.
   * @param requestTimeout the number of milliseconds the client should wait for a response from the
   *                       server before failing with an error.
   */
  public void execute(
      final AwsRequest request,
      final Long requestTimeout
  ) {
    service.callFunction(
        Fields.EXECUTE_ACTION_NAME,
        Collections.singletonList(getRequestArgs(request)),
        requestTimeout);
  }

  /**
   * Executes the AWS request, and decodes the result into an instance of the
   * specified type. The response will be decoded using the codec registry specified when the app
   * client was configured. If no codec registry was configured, a default codec registry will be
   * used. The default codec registry supports the mappings specified <a
   * href="http://mongodb.github.io/mongo-java-driver/3.1/bson/documents/#document">here</a>
   *
   * @param request the AWS request to execute.
   * @param requestTimeout the number of milliseconds the client should wait for a response from the
   *                       server before failing with an error.
   * @param resultDecoder the {@link Decoder} to use to decode the result into a value.
   * @param <ResultT> the type into which the response will be decoded.
   * @return the decoded result value.
   */
  public <ResultT> ResultT execute(
      final AwsRequest request,
      final Long requestTimeout,
      final Decoder<ResultT> resultDecoder
  ) {
    return service.callFunction(
        Fields.EXECUTE_ACTION_NAME,
        Collections.singletonList(getRequestArgs(request)),
        requestTimeout,
        resultDecoder);
  }

  /**
   * Executes the AWS request, and decodes the result into an instance of the
   * specified type. The response will be decoded using the codec registry specified when the app
   * client was configured. If no codec registry was configured, a default codec registry will be
   * used. The default codec registry supports the mappings specified <a
   * href="http://mongodb.github.io/mongo-java-driver/3.1/bson/documents/#document">here</a>
   *
   * @param request the AWS request to execute.
   * @param requestTimeout the number of milliseconds the client should wait for a response from the
   *                       server before failing with an error.
   * @param resultClass the class that the result should be decoded as.
   * @param <ResultT> the type into which the response will be decoded.
   * @return the decoded result value.
   */
  public <ResultT> ResultT execute(
      final AwsRequest request,
      final Long requestTimeout,
      final Class<ResultT> resultClass
  ) {
    return service.callFunction(
        Fields.EXECUTE_ACTION_NAME,
        Collections.singletonList(getRequestArgs(request)),
        requestTimeout,
        resultClass);
  }

  /**
   * Executes the AWS request, and decodes the result into an instance of the
   * specified type. The response will be decoded using the codec registry given.
   *
   * @param request the AWS request to execute.
   * @param requestTimeout the number of milliseconds the client should wait for a response from the
   *                       server before failing with an error.
   * @param resultClass the class that the result should be decoded as.
   * @param codecRegistry the codec registry used for de/serialization of the function call.
   * @param <ResultT> the type into which the response will be decoded.
   * @return the decoded result value.
   */
  public <ResultT> ResultT execute(
      final AwsRequest request,
      final Long requestTimeout,
      final Class<ResultT> resultClass,
      final CodecRegistry codecRegistry
  ) {
    return service.callFunction(
        Fields.EXECUTE_ACTION_NAME,
        Collections.singletonList(getRequestArgs(request)),
        resultClass,
        codecRegistry);
  }

  /**
   * Get the codec registry that will be used to decode responses when a codec registry.
   *
   * @return the {@link CodecRegistry}
   */
  public CodecRegistry getCodecRegistry() {
    return service.getCodecRegistry();
  }

  /**
   * Create a new CoreAwsServiceClient instance with a different codec registry.
   *
   * @param codecRegistry the new {@link CodecRegistry} for the client.
   * @return a new CoreAwsServiceClient instance with the different codec registry
   */
  public CoreAwsServiceClient withCodecRegistry(final CodecRegistry codecRegistry) {
    return new CoreAwsServiceClient(service.withCodecRegistry(codecRegistry));
  }

  private Document getRequestArgs(final AwsRequest request) {
    final Document args = new Document();
    args.put(Fields.SERVICE_PARAM, request.getService());
    args.put(Fields.ACTION_PARAM, request.getAction());
    args.put(Fields.ARGUMENTS_PARAM, request.getArguments());

    if (request.getRegion() != null) {
      args.put(Fields.REGION_PARAM, request.getRegion());
    }
    return args;
  }

  private static class Fields {
    static final String EXECUTE_ACTION_NAME = "execute";
    static final String SERVICE_PARAM = "aws_service";
    static final String ACTION_PARAM = "aws_action";
    static final String REGION_PARAM = "aws_region";
    static final String ARGUMENTS_PARAM = "aws_arguments";
  }
}
