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
import org.bson.Document;

public class StitchRequest {
  private final Method method;
  private final String path;
  private final Long timeout;
  private final Map<String, String> headers;
  private final byte[] body;
  private final Long startedAt;

  StitchRequest(final StitchRequest req) {
    this.method = req.method;
    this.path = req.path;
    this.timeout = req.timeout;
    this.headers = req.headers;
    this.body = req.body;
    this.startedAt = req.startedAt;
  }

  StitchRequest(
      final Method method,
      final String path,
      final Long timeout,
      final Map<String, String> headers,
      final byte[] body,
      final Long startedAt) {
    this.method = method;
    this.path = path;
    this.timeout = timeout;
    this.headers = headers;
    this.body = body;
    this.startedAt = startedAt;
  }

  public Builder builder() {
    return new Builder(this);
  }

  /**
   * Returns the HTTP method of the request.
   */
  public Method getMethod() {
    return method;
  }

  /**
   * Returns the Stitch API path of the request.
   */
  public String getPath() {
    return path;
  }

  /**
   * Returns the number of milliseconds that the underlying transport should spend on an HTTP round
   * trip before failing with an error. If not configured, a default should override it before the
   * request is transformed into a plain HTTP request.
   */
  public Long getTimeout() {
    return timeout;
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
    return Arrays.copyOf(body, body.length);
  }

  public Long getStartedAt() {
    return startedAt;
  }

  /**
   * A builder that can build {@link StitchRequest}s.
   */
  public static class Builder {
    private Method method;
    private String path;
    private Long timeout;
    private Map<String, String> headers = new HashMap<>();
    private byte[] body;
    private Long startedAt;

    Builder(final StitchRequest request) {
      method = request.method;
      path = request.path;
      timeout = request.timeout;
      headers = request.headers;
      body = request.body;
      startedAt = request.startedAt;
    }

    public Builder() {}

    /**
     * Sets the HTTP method of the request.
     */
    public Builder withMethod(final Method method) {
      this.method = method;
      return this;
    }

    /**
     * Sets the Stitch API path of the request.
     */
    public Builder withPath(final String path) {
      this.path = path;
      return this;
    }

    /**
     * Sets he number of milliseconds that the underlying transport should spend on an HTTP round
     * trip before failing with an error. If not configured, a default should override it before
     * the request is transformed into a plain HTTP request.
     */
    public Builder withTimeout(final Long timeout) {
      this.timeout = timeout;
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
     * Returns the HTTP method of the request.
     */
    public Method getMethod() {
      return this.method;
    }

    /**
     * Returns the Stitch API path of the request.
     */
    public String getPath() {
      return this.path;
    }

    /**
     * Returns the number of milliseconds that the underlying transport should spend on an HTTP
     * round trip before failing with an error. If not configured, a default should override it
     * before the request is transformed into a plain HTTP request.
     */
    public Long getTimeout() {
      return timeout;
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
     * Builds, validates, and returns the {@link StitchRequest}.
     */
    public StitchRequest build() {
      if (method == null) {
        throw new IllegalArgumentException("must set method");
      }
      if (path == null || path.isEmpty()) {
        throw new IllegalArgumentException("must set non-empty path");
      }
      if (startedAt == null) {
        startedAt = System.currentTimeMillis() / 1000L;
      }
      return new StitchRequest(
              method,
              path,
              timeout,
              headers == null ? new HashMap<String, String>() : headers,
              body,
              startedAt
      );
    }
  }

  Document toDocument() {
    final Document doc = new Document("method", getMethod().toString());
    doc.put("body", getBody());
    doc.put("headers", getHeaders());
    doc.put("path", getPath());
    return doc;
  }

  @Override
  public String toString() {
    return toDocument().toJson();
  }

  @Override
  public boolean equals(final Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof StitchRequest)) {
      return false;
    }
    final StitchRequest other = (StitchRequest) object;
    return getMethod().equals(other.getMethod())
        && Arrays.equals(getBody(), other.getBody())
        && getHeaders().equals(other.getHeaders())
        && getPath().equals(other.getPath());
  }

  @Override
  public int hashCode() {
    return getMethod().hashCode()
        + Arrays.hashCode(getBody())
        + getHeaders().hashCode()
        + getPath().hashCode();
  }
}
