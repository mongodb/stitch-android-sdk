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

package com.mongodb.stitch.core.auth.internal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongodb.stitch.core.internal.common.StitchObjectMapper;
import java.io.IOException;

import org.bson.Document;
import org.bson.internal.Base64;

final class Jwt {

  private final Long expires;
  private final Long issuedAt;
  private final Document userData;

  @JsonCreator
  private Jwt(
      @JsonProperty(Fields.EXPIRES) final Long expires,
      @JsonProperty(Fields.ISSUED_AT) final Long issuedAt,
      @JsonProperty(Fields.USER_DATA) final Document userData) {
    this.expires = expires;
    this.issuedAt = issuedAt;
    this.userData = userData;
  }

  static Jwt fromEncoded(final String encodedJwt) throws IOException {
    final String[] parts = splitToken(encodedJwt);
    final byte[] json = Base64.decode(parts[1]);
    return StitchObjectMapper.getInstance().readValue(json, Jwt.class);
  }

  private static String[] splitToken(final String jwt) throws IOException {
    final String[] parts = jwt.split("\\.");
    if (parts.length != 3) {
      throw new IOException(
          String.format("Malformed Jwt token. The string %s should have 3 parts.", jwt));
    }
    return parts;
  }

  Long getExpires() {
    return expires;
  }

  Long getIssuedAt() {
    return issuedAt;
  }

  Document getUserData() {
    return userData;
  }

  private static class Fields {
    private static final String EXPIRES = "exp";
    private static final String ISSUED_AT = "iat";
    private static final String USER_DATA = "user_data";
  }
}
