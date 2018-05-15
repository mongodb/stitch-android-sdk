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

package com.mongodb.stitch.core.services.http.internal;

import static com.mongodb.stitch.core.internal.common.Assertions.keyPresent;

import com.mongodb.stitch.core.services.http.HttpCookie;
import com.mongodb.stitch.core.services.http.HttpResponse;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.BsonReader;
import org.bson.Document;
import org.bson.codecs.Decoder;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodec;
import org.bson.types.Binary;

class ResultDecoders {

  static final Decoder<HttpResponse> httpResponseDecoder = new HttpResponseDecoder();

  private static final class HttpResponseDecoder implements Decoder<HttpResponse> {
    @Override
    public HttpResponse decode(
        final BsonReader reader,
        final DecoderContext decoderContext
    ) {
      final Document document = (new DocumentCodec()).decode(reader, decoderContext);
      keyPresent(Fields.STATUS_FIELD, document);
      keyPresent(Fields.STATUS_CODE_FIELD, document);
      keyPresent(Fields.CONTENT_LENGTH_FIELD, document);
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
          keyPresent(Fields.COOKIE_VALUE_FIELD, cookieValues);
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
        body = new byte[0];
      }

      return new HttpResponse(
          status,
          statusCode,
          contentLength,
          headers,
          cookies,
          body);
    }

    private static final class Fields {
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
}
