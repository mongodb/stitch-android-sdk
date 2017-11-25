package com.mongodb.stitch.android.http;

/**
 * HTTP Header definitions and helper methods.
 */
public class Headers {
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String AUTHORIZATION = "Authorization";
    public static final String AUTHORIZATION_BEARER = "Bearer";

    /**
     * @param value The bearer value
     * @return A standard Authorization Bearer header value.
     */
    public static String GetAuthorizationBearer(final String value) {
        return String.format("%s %s", AUTHORIZATION_BEARER, value);
    }
}
