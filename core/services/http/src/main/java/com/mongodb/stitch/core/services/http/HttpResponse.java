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

package com.mongodb.stitch.core.services.http;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The response to an HTTP request over the HTTP service.
 */
public class HttpResponse {
  private final String status;
  private final int statusCode;
  private final long contentLength;
  private final Map<String, Collection<String>> headers;
  private final Map<String, HttpCookie> cookies;
  private final byte[] body;

  /**
   * Constructs a new response to an HTTP request.
   *
   * @param status the human readable status of the response.
   * @param statusCode the status code of the response.
   * @param contentLength the content length of the response.
   * @param headers the response headers.
   * @param cookies the response cookies.
   * @param body the response body.
   */
  public HttpResponse(
      final String status,
      final int statusCode,
      final long contentLength,
      final Map<String, Collection<String>> headers,
      final Map<String, HttpCookie> cookies,
      final byte[] body
  ) {
    this.status = status;
    this.statusCode = statusCode;
    this.contentLength = contentLength;
    this.headers = headers;
    this.cookies = cookies;
    this.body = Arrays.copyOf(body, body.length);
  }

  /**
   * Returns the human readable status of the response.
   *
   * @return the human readable status of the response.
   */
  @Nonnull
  public String getStatus() {
    return status;
  }

  /**
   * Returns the status code of the response.
   *
   * @return the status code of the response.
   */
  public int getStatusCode() {
    return statusCode;
  }

  /**
   * Returns the content length of the response.
   *
   * @return the content length of the response.
   */
  public long getContentLength() {
    return contentLength;
  }

  /**
   * Returns the response headers.
   *
   * @return the response headers.
   */
  @Nullable
  public Map<String, Collection<String>> getHeaders() {
    return headers;
  }

  /**
   * Returns the response cookies.
   *
   * @return the response cookies.
   */
  @Nullable
  public Map<String, HttpCookie> getCookies() {
    return cookies;
  }

  /**
   * Returns a copy of the response body.
   *
   * @return a copy of the response body.
   */
  @Nullable
  public byte[] getBody() {
    return Arrays.copyOf(body, body.length);
  }
}
