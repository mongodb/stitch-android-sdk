package com.mongodb.stitch.core.internal.net;

import java.io.InputStream;
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
}
