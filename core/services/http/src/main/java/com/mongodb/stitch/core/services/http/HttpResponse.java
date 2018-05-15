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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonReader;
import org.bson.Document;
import org.bson.codecs.Decoder;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodec;
import org.bson.types.Binary;

public class HttpResponse {
  private final String status;
  private final int statusCode;
  private final long contentLength;
  private final Map<String, Collection<String>> headers;
  private final Map<String, HttpCookie> cookies;
  private final byte[] body;

  HttpResponse(
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
    this.body = body;
  }

  /**
   * Returns the human readable status of the response.
   */
  @Nonnull
  public String getStatus() {
    return status;
  }

  /**
   * Returns the status code of the response.
   */
  public int getStatusCode() {
    return statusCode;
  }

  /**
   * Returns the content length of the response.
   */
  public long getContentLength() {
    return contentLength;
  }

  /**
   * Returns the response headers.
   */
  @Nullable
  public Map<String, Collection<String>> getHeaders() {
    return headers;
  }

  /**
   * Returns the response cookies.
   */
  @Nullable
  public Map<String, HttpCookie> getCookies() {
    return cookies;
  }

  /**
   * Returns a copy of the response body.
   */
  @Nullable
  public byte[] getBody() {
    return Arrays.copyOf(body, body.length);
  }

  static Decoder<HttpResponse> Decoder = new Decoder<HttpResponse>() {
    @Override
    public HttpResponse decode(
        final BsonReader reader,
        final DecoderContext decoderContext
    ) {
      final Document document = (new DocumentCodec()).decode(reader, decoderContext);
      if (!document.containsKey(Fields.STATUS_FIELD)) {
        throw new IllegalStateException(
            String.format("expected %s to be present", Fields.STATUS_FIELD));
      }
      if (!document.containsKey(Fields.STATUS_CODE_FIELD)) {
        throw new IllegalStateException(
            String.format("expected %s to be present", Fields.STATUS_CODE_FIELD));
      }
      if (!document.containsKey(Fields.CONTENT_LENGTH_FIELD)) {
        throw new IllegalStateException(
            String.format("expected %s to be present", Fields.CONTENT_LENGTH_FIELD));
      }
      final String status = document.getString(Fields.STATUS_FIELD);
      final int statusCode = document.getInteger(Fields.STATUS_CODE_FIELD);
      final long contentLength = document.getLong(Fields.CONTENT_LENGTH_FIELD);

      final Map<String, Collection<String>> headers;
      if (document.containsKey(Fields.HEADERS_FIELD)) {
        headers = new HashMap<>();
        final Document headersDoc = document.get(Fields.HEADERS_FIELD, Document.class);
        for (final Map.Entry<String, Object> header : headersDoc.entrySet()) {
          @SuppressWarnings("unchecked")
          final List<String> values = (List<String>) header.getValue();
          headers.put(header.getKey(), values);
        }
      } else {
        headers = null;
      }

      final Map<String, HttpCookie> cookies;
      if (document.containsKey(Fields.COOKIES_FIELD)) {
        cookies = new HashMap<>();
        final Document cookiesDoc = document.get(Fields.COOKIES_FIELD, Document.class);
        for (final Map.Entry<String, Object> header : cookiesDoc.entrySet()) {
          final String name = header.getKey();
          @SuppressWarnings("unchecked")
          final Document cookieValues = (Document) header.getValue();
          if (!cookieValues.containsKey(Fields.COOKIE_VALUE_FIELD)) {
            throw new IllegalStateException(
                String.format("expected %s to be present", Fields.COOKIE_VALUE_FIELD));
          }
          final String value = cookieValues.getString(Fields.COOKIE_VALUE_FIELD);

          final String path;
          if (cookieValues.containsKey(Fields.COOKIE_PATH_FIELD)) {
            path = cookieValues.getString(Fields.COOKIE_PATH_FIELD);
          } else {
            path = null;
          }
          final String domain;
          if (cookieValues.containsKey(Fields.COOKIE_DOMAIN_FIELD)) {
            domain = cookieValues.getString(Fields.COOKIE_DOMAIN_FIELD);
          } else {
            domain = null;
          }
          final String expires;
          if (cookieValues.containsKey(Fields.COOKIE_EXPIRES_FIELD)) {
            expires = cookieValues.getString(Fields.COOKIE_EXPIRES_FIELD);
          } else {
            expires = null;
          }
          final Integer maxAge;
          if (cookieValues.containsKey(Fields.COOKIE_MAX_AGE_FIELD)) {
            maxAge = cookieValues.getInteger(Fields.COOKIE_MAX_AGE_FIELD);
          } else {
            maxAge = null;
          }
          final Boolean secure;
          if (cookieValues.containsKey(Fields.COOKIE_SECURE_FIELD)) {
            secure = cookieValues.getBoolean(Fields.COOKIE_SECURE_FIELD);
          } else {
            secure = null;
          }
          final Boolean httpOnly;
          if (cookieValues.containsKey(Fields.COOKIE_HTTP_ONLY_FIELD)) {
            httpOnly = cookieValues.getBoolean(Fields.COOKIE_HTTP_ONLY_FIELD);
          } else {
            httpOnly = null;
          }

          cookies.put(header.getKey(), new HttpCookie(
              name,
              value,
              path,
              domain,
              expires,
              maxAge,
              secure,
              httpOnly
          ));
        }
      } else {
        cookies = null;
      }

      final byte[] body;
      if (document.containsKey(Fields.BODY_FIELD)) {
        body = document.get(Fields.BODY_FIELD, Binary.class).getData();
      } else {
        body = null;
      }

      return new HttpResponse(
          status,
          statusCode,
          contentLength,
          headers,
          cookies,
          body);
    }
  };

  private static class Fields {
    static final String STATUS_FIELD = "status";
    static final String STATUS_CODE_FIELD = "statusCode";
    static final String CONTENT_LENGTH_FIELD = "contentLength";
    static final String HEADERS_FIELD = "headers";
    static final String COOKIES_FIELD = "cookies";
    static final String BODY_FIELD = "body";

    static final String COOKIE_VALUE_FIELD = "value";
    static final String COOKIE_PATH_FIELD = "path";
    static final String COOKIE_DOMAIN_FIELD = "domain";
    static final String COOKIE_EXPIRES_FIELD = "expires";
    static final String COOKIE_MAX_AGE_FIELD = "maxAge";
    static final String COOKIE_SECURE_FIELD = "secure";
    static final String COOKIE_HTTP_ONLY_FIELD = "httpOnly";
  }
}
