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

import com.mongodb.stitch.core.StitchRequestErrorCode;
import com.mongodb.stitch.core.StitchRequestException;
import com.mongodb.stitch.core.internal.common.StitchError;

public class StitchRequestClient {
  protected final String baseUrl;
  protected final Transport transport;

  private final Long defaultRequestTimeout;

  /**
   * Constructs a StitchRequestClient with the provided parameters.
   * @param baseUrl the base URL of the Stitch server to which this client will make requests.
   * @param transport the underlying {@link Transport} that this client will use to make requests.
   * @param defaultRequestTimeout the number of milliseconds the client should wait for a response
   *                              by default from the server before failing with an error.
   */
  public StitchRequestClient(final String baseUrl,
                             final Transport transport,
                             final Long defaultRequestTimeout) {
    this.baseUrl = baseUrl;
    this.transport = transport;
    this.defaultRequestTimeout = defaultRequestTimeout;
  }

  protected static void inspectResponse(final Response response) {
    if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
      return;
    }

    StitchError.handleRequestError(response);
  }

  /**
   * Performs a request against global Stitch app server. Throws a Stitch specific exception
   * if the request fails.
   * @param stitchReq the request to perform.
   * @return a {@link Response} to the request.
   */
  public Response doRequest(final StitchRequest stitchReq) {
    return doRequest(stitchReq, baseUrl);
  }

  Response doRequest(final StitchRequest stitchReq, final String url) {
    final Response response;
    try {
      response = transport.roundTrip(buildRequest(stitchReq, url));
    } catch (Exception e) {
      throw new StitchRequestException(e, StitchRequestErrorCode.TRANSPORT_ERROR);
    }

    inspectResponse(response);

    return response;
  }

  /**
   * Performs a streaming request against global Stitch app server. Throws a Stitch-specific
   * exception if the request fails.
   *
   * @param stitchReq the request to perform.
   * @return an {@link EventStream} that will provide response events.
   */
  public EventStream doStreamRequest(final StitchRequest stitchReq) {
    return doStreamRequest(stitchReq, baseUrl);
  }

  EventStream doStreamRequest(final StitchRequest stitchReq, final String url) {
    try {
      return transport.stream(buildRequest(stitchReq, url));
    } catch (Exception e) {
      throw new StitchRequestException(e, StitchRequestErrorCode.TRANSPORT_ERROR);
    }
  }

  Request buildRequest(final StitchRequest stitchReq, final String url) {
    return new Request.Builder()
        .withMethod(stitchReq.getMethod())
        .withUrl(String.format("%s%s", url, stitchReq.getPath()))
        .withTimeout(
                stitchReq.getTimeout() == null ? defaultRequestTimeout : stitchReq.getTimeout())
        .withHeaders(stitchReq.getHeaders())
        .withBody(stitchReq.getBody())
        .build();
  }

  public void close() {
    transport.close();
  }
}
