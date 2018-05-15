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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A response to an HTTP request, typically originating from a {@link Request}.
 */
public final class Response {
  private final int statusCode;
  private final Map<String, String> headers;
  private final InputStream body;

  /**
   * Constructs a response from a status code, headers, and bodu.
   *
   * @param statusCode the status code of the response.
   * @param headers the headers of the response.
   * @param body the body of the response.
   */
  public Response(
      final int statusCode, final Map<String, String> headers, final InputStream body) {
    this.statusCode = statusCode;
    this.body = body;
    if (headers == null) {
      this.headers = Collections.emptyMap();
      return;
    }

    final Map<String, String> processedHeaders = new HashMap<>();
    for (final Map.Entry<String, String> kv : headers.entrySet()) {
      processedHeaders.put(kv.getKey().toLowerCase(Locale.US), kv.getValue());
    }
    this.headers = processedHeaders;
  }

  /**
   * Constructs a response from a status code and body.
   *
   * @param statusCode the status code of the response.
   * @param body the body of the response.
   */
  public Response(final int statusCode, final InputStream body) {
    this(statusCode, Collections.<String, String>emptyMap(), body);
  }

  /**
   * Constructs a response from a status code and raw body bytes.
   *
   * @param statusCode the status code of the response.
   * @param body the body of the response as raw bytes.
   */
  public Response(final int statusCode, final byte[] body) {
    this(statusCode, Collections.<String, String>emptyMap(), new ByteArrayInputStream(body));
  }

  /**
   * Constructs a response from a status, headers, and a body represented by a {@link String}.
   *
   * @param statusCode the status code of the response.
   * @param headers the headers of the response.
   * @param body the body of the response as a string.
   */
  public Response(final int statusCode, final Map<String, String> headers, final String body) {
    this(
        statusCode,
        headers,
        new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
  }

  /**
   * Constructs a response from a status and a body represented by a {@link String}.
   *
   * @param statusCode the status code of the response.
   * @param body the body of the response as a string.
   */
  public Response(final int statusCode, final String body) {
    this(
        statusCode,
        Collections.<String, String>emptyMap(),
        new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
  }

  /**
   * Constructs a response from a status and headers.
   *
   * @param statusCode the status code of the response.
   * @param headers the headers of the response.
   */
  public Response(final int statusCode, final Map<String, String> headers) {
    this(
        statusCode,
        headers,
        (InputStream) null);
  }

  /**
   * Constructs a successful (200 OK) response from raw body bytes.
   *
   * @param body the body of the response as raw bytes.
   */
  public Response(final byte[] body) {
    this(200, body);
  }

  /**
   * Constructs a successful (200 OK) response from a body represented by a {@link String}.
   *
   * @param body the body of the response as a string.
   */
  public Response(final String body) {
    this(200, body);
  }

  /**
   * Constructs a response from the given status code.
   *
   * @param statusCode the status code of the response.
   */
  public Response(final int statusCode) {
    this.statusCode = statusCode;
    this.headers = Collections.emptyMap();
    this.body = null;
  }

  /** Returns the status code of the response. */
  public int getStatusCode() {
    return statusCode;
  }

  /** Returns the headers of the response. */
  public Map<String, String> getHeaders() {
    return headers;
  }

  /** Returns the body of the response; may be null. */
  public InputStream getBody() {
    return body;
  }
}
