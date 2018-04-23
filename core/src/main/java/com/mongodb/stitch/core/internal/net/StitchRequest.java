package com.mongodb.stitch.core.internal.net;

import java.util.HashMap;
import java.util.Map;

public class StitchRequest {
  public final Method method;
  public final String path;
  public final Map<String, String> headers;
  public final byte[] body;
  public final Long startedAt;

  StitchRequest(final StitchRequest req) {
    this.method = req.method;
    this.path = req.path;
    this.headers = req.headers;
    this.body = req.body;
    this.startedAt = req.startedAt;
  }

  StitchRequest(
      final Method method,
      final String path,
      final Map<String, String> headers,
      final byte[] body,
      final Long startedAt) {
    this.method = method;
    this.path = path;
    this.headers = headers;
    this.body = body;
    this.startedAt = startedAt;
  }

  public Builder builder() {
    return new Builder(this);
  }

  public static class Builder {
    private Method method;
    private String path;
    private Map<String, String> headers = new HashMap<>();
    private byte[] body;
    private Long startedAt;

    Builder(final StitchRequest request) {
      method = request.method;
      path = request.path;
      headers = request.headers;
      body = request.body;
      startedAt = request.startedAt;
    }

    public Builder() {}

    public Builder withMethod(final Method method) {
      this.method = method;
      return this;
    }

    public Builder withPath(final String path) {
      this.path = path;
      return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    public Builder withHeaders(final Map<String, String> headers) {
      this.headers = headers;
      return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    public Builder withBody(final byte[] body) {
      this.body = body;
      return this;
    }

    public Method getMethod() {
      return this.method;
    }

    public String getPath() {
      return this.path;
    }

    public Map<String, String> getHeaders() {
      return this.headers;
    }

    public byte[] getBody() {
      return this.body;
    }

    public StitchRequest build() {
      if (method == null) {
        throw new IllegalStateException("must set method");
      }
      if (path == null || path.isEmpty()) {
        throw new IllegalStateException("must set non-empty path");
      }
      if (startedAt == null) {
        startedAt = System.currentTimeMillis() / 1000L;
      }
      return new StitchRequest(
          method, path, headers == null ? new HashMap<String, String>() : headers, body, startedAt);
    }
  }
}
