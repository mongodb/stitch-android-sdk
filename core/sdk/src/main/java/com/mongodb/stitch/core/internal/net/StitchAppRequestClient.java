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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongodb.stitch.core.StitchRequestErrorCode;
import com.mongodb.stitch.core.StitchRequestException;
import com.mongodb.stitch.core.internal.common.IoUtils;
import com.mongodb.stitch.core.internal.common.StitchObjectMapper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

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
    if (appMetadata == null) {
      init(clientAppId);
    }
    // init throws if no location is available, so this should be safe

    final Response response;
    try {
      response = transport.roundTrip(buildRequest(stitchReq, appMetadata.hostname));
    } catch (Exception e) {
      throw new StitchRequestException(e, StitchRequestErrorCode.TRANSPORT_ERROR);
    }

    inspectResponse(response);

    return response;
  }

  /**
   * Performs a streaming request against a Stitch app server determined by the deployment model
   * of the underlying app. Throws a Stitch specific exception if the request fails.
   *
   * @param stitchReq the request to perform.
   * @return a {@link EventStream} representing the result of the request.
   */
  @Override
  public EventStream doStreamRequest(final StitchRequest stitchReq) {
    if (appMetadata == null) {
      init(clientAppId);
    }
    // init throws if no location is available, so this should be safe

    try {
      return transport.stream(buildRequest(stitchReq, appMetadata.hostname));
    } catch (Exception e) {
      throw new StitchRequestException(e, StitchRequestErrorCode.TRANSPORT_ERROR);
    }
  }

  private synchronized void init(final String clientAppId) {
    // double-check, in case two threads hit this point simultaneously
    if (appMetadata != null) {
      return;
    }

    final StitchAppRoutes routes = new StitchAppRoutes(clientAppId);

    final StitchRequest bootstrapStitchRequest = new StitchRequest.Builder()
        .withMethod(Method.GET)
        .withPath(routes.getServiceRoutes().getLocationRoute())
        .build();
    final Request bootstrapRequest = buildRequest(bootstrapStitchRequest, baseUrl);

    final Response response;
    try {
      response = transport.roundTrip(bootstrapRequest);
    } catch (Exception e) {
      throw new StitchRequestException(e, StitchRequestErrorCode.TRANSPORT_ERROR);
    }

    inspectResponse(response);

    final AppMetadata responseMetadata;
    try {
      responseMetadata = StitchObjectMapper.getInstance()
          .readValue(IoUtils.readAllToString(response.getBody()), AppMetadata.class);

    } catch (Exception e) {
      throw new StitchRequestException(e, StitchRequestErrorCode.DECODING_ERROR);
    }

    if (responseMetadata != null && responseMetadata.hostname != null) {
      appMetadata = responseMetadata;
    } else {
      throw new StitchRequestException(
          String.format(BOOTSTRAP_ERROR_MESSAGE_INVALID_HOSTNAME,
              appMetadata == null ? "null" : appMetadata),
          StitchRequestErrorCode.BOOTSTRAP_ERROR);
    }
  }

  @SuppressWarnings("unused") // Jackson uses reflection
  static class AppMetadata {
    String deploymentModel;
    String location;
    String hostname;

    @JsonProperty("deployment_model")
    public String getDeploymentModel() {
      return deploymentModel;
    }

    public void setDeploymentModel(final String deploymentModel) {
      this.deploymentModel = deploymentModel;
    }

    @JsonProperty("location")
    public String getLocation() {
      return location;
    }

    public void setLocation(final String location) {
      this.location = location;
    }

    @JsonProperty("hostname")
    public String getHostname() {
      return hostname;
    }

    public void setHostname(final String hostname) {
      this.hostname = hostname;
    }

    @Override
    public String toString() {
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      try {
        StitchObjectMapper.getInstance().writeValue(out, this);
      } catch (Exception e) {
        return "<json serialization error>";
      }

      return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
  }
}
