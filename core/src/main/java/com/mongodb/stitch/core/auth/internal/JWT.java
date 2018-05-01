package com.mongodb.stitch.core.auth.internal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongodb.stitch.core.internal.common.StitchObjectMapper;
import java.io.IOException;
import org.bson.internal.Base64;

final class JWT {

  private final Long expires;
  private final Long issuedAt;

  @JsonCreator
  private JWT(
      @JsonProperty(Fields.EXPIRES) final Long expires,
      @JsonProperty(Fields.ISSUED_AT) final Long issuedAt) {
    this.expires = expires;
    this.issuedAt = issuedAt;
  }

  static JWT fromEncoded(final String encodedJWT) throws IOException {
    final String[] parts = splitToken(encodedJWT);
    final byte[] json = Base64.decode(parts[1]);
    return StitchObjectMapper.getInstance().readValue(json, JWT.class);
  }

  private static String[] splitToken(final String jwt) throws IOException {
    final String[] parts = jwt.split("\\.");
    if (parts.length != 3) {
      throw new IOException(
          String.format("Malformed JWT token. The string %s should have 3 parts.", jwt));
    }
    return parts;
  }

  Long getExpires() {
    return expires;
  }

  Long getIssuedAt() {
    return issuedAt;
  }

  private static class Fields {
    private static final String EXPIRES = "exp";
    private static final String ISSUED_AT = "iat";
  }
}
