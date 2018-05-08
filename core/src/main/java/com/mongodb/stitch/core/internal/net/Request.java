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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * A request to perform against a remote server.
 */
public final class Request {
  private final Method method;
  private final String url;
  private final Map<String, String> headers;
  private final byte[] body;

  private Request(
      final Method method, final String url, final Map<String, String> headers, final byte[] body) {
    this.method = method;
    this.url = url;
    this.headers = headers;
    this.body = body;
  }

  /**
   * Returns the URL that the request will be performed against.
   */
  public String getUrl() {
    return url;
  }

  /**
   * Returns the headers that will be included in the request.
   */
  public Map<String, String> getHeaders() {
    return headers;
  }

  /**
   * Returns a copy of the body that will be sent along with the request.
   */
  public byte[] getBody() {
    if (body == null) {
      return null;
    }
    return Arrays.copyOf(this.body, this.body.length);
  }

  /**
   * Returns the HTTP method of the request.
   */
  public Method getMethod() {
    return method;
  }

  /**
   * A builder that can build {@link Request}s.
   */
  public static class Builder {
    private Method method;
    private String url;
    private Map<String, String> headers;
    private byte[] body;

    public Builder() {}

    private Builder(final Request request) {
      method = request.method;
      url = request.url;
      headers = request.headers;
      body = request.body;
    }

    /**
     * Sets the URL that the request will be performed against.
     */
    public Builder withUrl(final String url) {
      this.url = url;
      return this;
    }

    /**
     * Sets the headers that will be included in the request.
     */
    public Builder withHeaders(final Map<String, String> headers) {
      this.headers = headers;
      return this;
    }

    /**
     * Sets a copy of the body that will be sent along with the request.
     */
    public Builder withBody(final byte[] body) {
      if (body == null) {
        return this;
      }
      this.body = Arrays.copyOf(body, body.length);
      return this;
    }

    /**
     * Sets the HTTP method of the request.
     */
    public Builder withMethod(final Method method) {
      this.method = method;
      return this;
    }

    /**
     * Returns the URL that the request will be performed against.
     */
    public String getUrl() {
      return this.url;
    }

    /**
     * Returns the headers that will be included in the request.
     */
    public Map<String, String> getHeaders() {
      return this.headers;
    }

    /**
     * Returns a copy of the body that will be sent along with the request.
     */
    public byte[] getBody() {
      if (body == null) {
        return null;
      }
      return Arrays.copyOf(this.body, this.body.length);
    }

    /**
     * Returns the HTTP method of the request.
     */
    public Method getMethod() {
      return this.method;
    }

    /**
     * Builds, validates, and returns the {@link Request}.
     */
    public Request build() {
      if (method == null) {
        throw new IllegalStateException("must set method");
      }
      if (url == null || url.isEmpty()) {
        throw new IllegalStateException("must set non-empty url");
      }
      return new Request(
          method, url, headers == null ? new HashMap<String, String>() : headers, body);
    }
  }
}
