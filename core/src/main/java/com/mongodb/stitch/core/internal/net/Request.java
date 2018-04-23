package com.mongodb.stitch.core.internal.net;

import java.util.HashMap;
import java.util.Map;

public final class Request {
  public final Method method;
  public final String url;
  public final Map<String, String> headers;
  public final byte[] body;

  private Request(
      final Method method, final String url, final Map<String, String> headers, final byte[] body) {
    this.method = method;
    this.url = url;
    this.headers = headers;
    this.body = body;
  }

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

    public Builder withMethod(final Method method) {
      this.method = method;
      return this;
    }

    public Builder withURL(final String url) {
      this.url = url;
      return this;
    }

    public Builder withHeaders(final Map<String, String> headers) {
      this.headers = headers;
      return this;
    }

    public Builder withBody(final byte[] body) {
      this.body = body;
      return this;
    }

    public Method getMethod() {
      return this.method;
    }

    public String getURL() {
      return this.url;
    }

    public Map<String, String> getHeaders() {
      return this.headers;
    }

    public byte[] getBody() {
      return this.body;
    }

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
