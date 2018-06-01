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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonReader;
import org.bson.BsonValue;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.Decoder;
import org.bson.codecs.DecoderContext;

class ResultDecoders {

  static final Decoder<HttpResponse> httpResponseDecoder = new HttpResponseDecoder();

  private static final class HttpResponseDecoder implements Decoder<HttpResponse> {
    @Override
    public HttpResponse decode(
        final BsonReader reader,
        final DecoderContext decoderContext
    ) {
      final BsonDocument document = (new BsonDocumentCodec()).decode(reader, decoderContext);
      keyPresent(Fields.STATUS_FIELD, document);
      keyPresent(Fields.STATUS_CODE_FIELD, document);
      keyPresent(Fields.CONTENT_LENGTH_FIELD, document);
      final String status = document.getString(Fields.STATUS_FIELD).getValue();
      final int statusCode = document.getNumber(Fields.STATUS_CODE_FIELD).intValue();
      final long contentLength = document.getNumber(Fields.CONTENT_LENGTH_FIELD).longValue();

      final Map<String, Collection<String>> headers;
      if (document.containsKey(Fields.HEADERS_FIELD)) {
        headers = new HashMap<>();
        final BsonDocument headersDoc = document.getDocument(Fields.HEADERS_FIELD);
        for (final Map.Entry<String, BsonValue> header : headersDoc.entrySet()) {
          final BsonArray valuesArr = header.getValue().asArray();
          final List<String> values = new ArrayList<>(valuesArr.size());
          for (final BsonValue value : valuesArr) {
            values.add(value.asString().getValue());
          }
          headers.put(header.getKey(), values);
        }
      } else {
        headers = null;
      }

      final Map<String, HttpCookie> cookies;
      if (document.containsKey(Fields.COOKIES_FIELD)) {
        cookies = new HashMap<>();
        final BsonDocument cookiesDoc = document.getDocument(Fields.COOKIES_FIELD);
        for (final Map.Entry<String, BsonValue> header : cookiesDoc.entrySet()) {
          final String name = header.getKey();
          final BsonDocument cookieValues = header.getValue().asDocument();
          keyPresent(Fields.COOKIE_VALUE_FIELD, cookieValues);
          final String value = cookieValues.getString(Fields.COOKIE_VALUE_FIELD).getValue();

          final String path;
          if (cookieValues.containsKey(Fields.COOKIE_PATH_FIELD)) {
            path = cookieValues.getString(Fields.COOKIE_PATH_FIELD).getValue();
          } else {
            path = null;
          }
          final String domain;
          if (cookieValues.containsKey(Fields.COOKIE_DOMAIN_FIELD)) {
            domain = cookieValues.getString(Fields.COOKIE_DOMAIN_FIELD).getValue();
          } else {
            domain = null;
          }
          final String expires;
          if (cookieValues.containsKey(Fields.COOKIE_EXPIRES_FIELD)) {
            expires = cookieValues.getString(Fields.COOKIE_EXPIRES_FIELD).getValue();
          } else {
            expires = null;
          }
          final Integer maxAge;
          if (cookieValues.containsKey(Fields.COOKIE_MAX_AGE_FIELD)) {
            maxAge = cookieValues.getNumber(Fields.COOKIE_MAX_AGE_FIELD).intValue();
          } else {
            maxAge = null;
          }
          final Boolean secure;
          if (cookieValues.containsKey(Fields.COOKIE_SECURE_FIELD)) {
            secure = cookieValues.getBoolean(Fields.COOKIE_SECURE_FIELD).getValue();
          } else {
            secure = null;
          }
          final Boolean httpOnly;
          if (cookieValues.containsKey(Fields.COOKIE_HTTP_ONLY_FIELD)) {
            httpOnly = cookieValues.getBoolean(Fields.COOKIE_HTTP_ONLY_FIELD).getValue();
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
        body = document.getBinary(Fields.BODY_FIELD).getData();
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
