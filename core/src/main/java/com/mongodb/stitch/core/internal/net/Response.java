package com.mongodb.stitch.core.internal.net;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

public final class Response {
  public final Integer statusCode;
  public final Map<String, String> headers;
  public final InputStream body;

  public Response(
      final Integer statusCode, final Map<String, String> headers, final InputStream body) {
    this.statusCode = statusCode;
    this.headers = headers;
    this.body = body;
  }

  public Response(
          final Integer statusCode, final InputStream body) {
    this(statusCode, Collections.emptyMap(), body);
  }

    public Response(
            final Integer statusCode, final byte[] body) {
        this(statusCode, Collections.emptyMap(), new ByteArrayInputStream(body));
    }

  public Response(
          final Integer statusCode, final String body) {
    this(statusCode, Collections.emptyMap(),
            new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
  }

    public Response(final byte[] body) {
        this(200, body);
    }

    public Response(final String body) {
        this(200, body);
    }
}
