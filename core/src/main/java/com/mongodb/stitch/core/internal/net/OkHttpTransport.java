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

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

public final class OkHttpTransport implements Transport {

  private final OkHttpClient client;

  public OkHttpTransport() {
    this.client = new OkHttpClient();
  }

  private static okhttp3.Request buildRequest(final Request request) {
    final okhttp3.Request.Builder reqBuilder =
        new okhttp3.Request.Builder()
            .url(request.getUrl())
            .headers(Headers.of(request.getHeaders()));
    if (request.getBody() != null) {
      String contentType =
          request.getHeaders().get(com.mongodb.stitch.core.internal.net.Headers.CONTENT_TYPE);
      contentType = contentType == null ? "" : contentType;
      final RequestBody body = RequestBody.create(MediaType.parse(contentType), request.getBody());
      reqBuilder.method(request.getMethod().toString(), body);
    } else {
      switch (request.getMethod()) {
        case POST:
        case PUT:
        case PATCH:
          reqBuilder.method(request.getMethod().toString(), RequestBody.create(null, ""));
          break;
        default:
          reqBuilder.method(request.getMethod().toString(), null);
          break;
      }
    }

    return reqBuilder.build();
  }

  private static Response handleResponse(final okhttp3.Response response) {
    final ResponseBody body = response.body();
    final InputStream bodyStream;
    if (body != null) {
      bodyStream = body.byteStream();
    } else {
      bodyStream = null;
    }
    final Integer statusCode = response.code();
    final Map<String, String> headers = new HashMap<>();
    for (Map.Entry<String, List<String>> entry : response.headers().toMultimap().entrySet()) {
      headers.put(entry.getKey(), entry.getValue().get(0));
    }
    return new Response(statusCode, headers, bodyStream);
  }

  @Override
  // This executes a request synchronously
  public Response roundTrip(final Request request) throws IOException {
    OkHttpClient reqClient = client.newBuilder().readTimeout(
            request.getTimeout(),
            TimeUnit.MILLISECONDS
    ).build();
    return handleResponse(reqClient.newCall(buildRequest(request)).execute());
  }
}
