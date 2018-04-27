package com.mongodb.stitch.core.internal.net;

import com.mongodb.stitch.core.StitchError;
import com.mongodb.stitch.core.StitchRequestErrorCode;
import com.mongodb.stitch.core.StitchRequestException;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class StitchRequestClient {

  private final String baseURL;
  private final Transport transport;
  private final Long transportTimeout;

  public StitchRequestClient(final String baseURL,
                             final Transport transport,
                             final Long transportTimeout) {
    this.baseURL = baseURL;
    this.transport = transport;
    this.transportTimeout = transportTimeout;
  }

  private static Response inspectResponse(final Response response) {
    if (response.statusCode >= 200 && response.statusCode < 300) {
      return response;
    }

    StitchError.handleRequestError(response);
    return null;
  }

  public Response doRequest(final StitchRequest stitchReq) {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<Response> future = executor.submit(new Callable<Response>() {
      @Override
      public Response call() {
        final Response response;
        try {
          response = transport.roundTrip(buildRequest(stitchReq));
        } catch (Exception e) {
          throw new StitchRequestException(e, StitchRequestErrorCode.TRANSPORT_ERROR);
        }

        return response;
      }
    });

    final Response response;
    try {
      response = future.get(transportTimeout, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      // Handle an interrupted thread, though this this is unlikely.
      throw new StitchRequestException(e, StitchRequestErrorCode.TRANSPORT_ERROR);
    } catch (ExecutionException e) {
      // Handle an underlying transport error.
      if (e.getCause() instanceof StitchRequestException) {
        throw (StitchRequestException) e.getCause();
      }
      throw new StitchRequestException(e, StitchRequestErrorCode.TRANSPORT_ERROR);
    } catch (TimeoutException e) {
      // Handle timeouts.
      future.cancel(true);
      throw new StitchRequestException(null, StitchRequestErrorCode.TRANSPORT_TIMEOUT_ERROR);
    } finally {
      executor.shutdown();
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
