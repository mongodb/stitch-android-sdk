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

package com.mongodb.stitch.core.internal.net;

public class StitchRequestClientImpl extends BaseStitchRequestClient {
  /**
   * Constructs a StitchRequestClientImpl with the provided parameters.
   * @param baseUrl the base URL of the Stitch server to which this client will make requests.
   * @param transport the underlying {@link Transport} that this client will use to make requests.
   * @param defaultRequestTimeout the number of milliseconds the client should wait for a response
   *                              by default from the server before failing with an error.
   */
  public StitchRequestClientImpl(
      final String baseUrl,
      final Transport transport,
      final Long defaultRequestTimeout) {
    super(baseUrl, transport, defaultRequestTimeout);
  }

  /**
   * Performs a request against global Stitch app server. Throws a Stitch specific exception
   * if the request fails.
   * @param stitchReq the request to perform.
   * @return a {@link Response} to the request.
   */
  @Override
  public Response doRequest(final StitchRequest stitchReq) {
    return doRequestUrl(stitchReq, baseUrl);
  }

  /**
   * Performs a streaming request against global Stitch app server. Throws a Stitch-specific
   * exception if the request fails.
   *
   * @param stitchReq the request to perform.
   * @return an {@link EventStream} that will provide response events.
   */
  @Override
  public EventStream doStreamRequest(final StitchRequest stitchReq) {
    return doStreamRequestUrl(stitchReq, baseUrl);
  }

}
