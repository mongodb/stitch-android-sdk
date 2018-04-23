package com.mongodb.stitch.core.internal.net;

import com.mongodb.stitch.core.StitchError;
import com.mongodb.stitch.core.StitchRequestErrorCode;
import com.mongodb.stitch.core.StitchRequestException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class StitchRequestClient {

  private final String baseURL;
  private final Transport transport;

  public StitchRequestClient(final String baseURL, final Transport transport) {
    this.baseURL = baseURL;
    this.transport = transport;
  }

  private static Response inspectResponse(final Response response) {
    if (response.statusCode >= 200 && response.statusCode < 300) {
      return response;
    }

    StitchError.handleRequestError(response);
    return null;
  }

  public Response doRequest(final StitchRequest stitchReq) {
    final Response response;
    try {
      response = transport.roundTrip(buildRequest(stitchReq));
    } catch (Exception e) {
      throw new StitchRequestException(e, StitchRequestErrorCode.TRANSPORT_ERROR);
    }

    return inspectResponse(response);
  }

  public Response doJSONRequestRaw(final StitchDocRequest stitchReq) {
    final StitchDocRequest.Builder newReqBuilder = stitchReq.builder();
    newReqBuilder.withBody(stitchReq.document.toJson().getBytes(StandardCharsets.UTF_8));
    final Map<String, String> newHeaders = newReqBuilder.getHeaders(); // This is not a copy
    newHeaders.put(Headers.CONTENT_TYPE, ContentTypes.APPLICATION_JSON);
    newReqBuilder.withHeaders(newHeaders);

    return doRequest(newReqBuilder.build());
  }

  private Request buildRequest(final StitchRequest stitchReq) {
    return new Request.Builder()
        .withMethod(stitchReq.method)
        .withURL(String.format("%s%s", baseURL, stitchReq.path))
        .withHeaders(stitchReq.headers)
        .withBody(stitchReq.body)
        .build();
  }
}
