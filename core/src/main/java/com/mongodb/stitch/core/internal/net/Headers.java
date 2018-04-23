package com.mongodb.stitch.core.internal.net;

/** HTTP Header definitions and helper methods. */
public final class Headers {
  public static final String CONTENT_TYPE = "Content-Type";
  public static final String AUTHORIZATION = "Authorization";
  private static final String AUTHORIZATION_BEARER = "Bearer";

  /**
   * @param value The bearer value
   * @return A standard Authorization Bearer header value.
   */
  public static String getAuthorizationBearer(final String value) {
    return String.format("%s %s", AUTHORIZATION_BEARER, value);
  }
}
