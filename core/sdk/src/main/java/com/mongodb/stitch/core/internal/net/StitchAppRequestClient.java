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
import com.mongodb.stitch.core.internal.common.IoUtils;
import com.mongodb.stitch.core.internal.common.StitchObjectMapper;

import java.io.IOException;

public class StitchAppRequestClient extends StitchRequestClient {
  private static final String BOOTSTRAP_ERROR_MESSAGE_INVALID_HOSTNAME =
      "invalid hostname in metadata: %s";

  private final String clientAppId;

  private AppMetadata appMetadata;

  public StitchAppRequestClient(
      final String clientAppId,
      final String baseUrl,
      final Transport transport,
      final Long defaultRequestTimeout
  ) {
    super(baseUrl, transport, defaultRequestTimeout);
    this.clientAppId = clientAppId;
  }

  /**
   * Performs a request against a Stitch app server determined by the deployment model
   * of the underlying app. Throws a Stitch specific exception if the request fails.
   *
   * @param stitchReq the request to perform.
   * @return a {@link Response} to the request.
   */
  @Override
  public Response doRequest(final StitchRequest stitchReq) {
    initAppMetadata(clientAppId);

    return super.doRequest(stitchReq, appMetadata.hostname);
  }

  /**
   * Performs a streaming request against a Stitch app server determined by the deployment model
   * of the underlying app. Throws a Stitch specific exception if the request fails.
   *
   * @param stitchReq the request to perform.
   * @return an {@link EventStream} that will provide response events.
   */
  @Override
  public EventStream doStreamRequest(final StitchRequest stitchReq) {
    initAppMetadata(clientAppId);

    return super.doStreamRequest(stitchReq, appMetadata.hostname);
  }

  private synchronized void initAppMetadata(final String clientAppId) {
    if (appMetadata != null) {
      return;
    }

    final StitchAppRoutes routes = new StitchAppRoutes(clientAppId);

    final StitchRequest bootstrapStitchRequest = new StitchRequest.Builder()
        .withMethod(Method.GET)
        .withPath(routes.getServiceRoutes().getLocationRoute())
        .build();

    final Response response = super.doRequest(bootstrapStitchRequest, baseUrl);
    final AppMetadata responseMetadata;
    try {
      responseMetadata = StitchObjectMapper.getInstance()
          .readValue(IoUtils.readAllToString(response.getBody()), AppMetadata.class);

    } catch (IOException e) {
      throw new StitchRequestException(e, StitchRequestErrorCode.DECODING_ERROR);
    }

    if (responseMetadata != null && responseMetadata.hostname != null
        && !"".equals(responseMetadata.hostname.trim())) {
      appMetadata = responseMetadata;
    } else {
      throw new StitchRequestException(
          String.format(BOOTSTRAP_ERROR_MESSAGE_INVALID_HOSTNAME,
              appMetadata == null ? "null" : appMetadata),
          StitchRequestErrorCode.BOOTSTRAP_ERROR);
    }
  }
}
