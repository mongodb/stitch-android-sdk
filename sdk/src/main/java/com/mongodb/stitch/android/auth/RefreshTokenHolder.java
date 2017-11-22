package com.mongodb.stitch.android.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RefreshTokenHolder is a container for the serialization of refresh tokens.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RefreshTokenHolder {

    @JsonProperty(Fields.REFRESH_TOKEN)
    private final String _refreshToken;

    @JsonCreator
    private RefreshTokenHolder(
            @JsonProperty(Fields.REFRESH_TOKEN)
            final String refreshToken
    ) {
        _refreshToken = refreshToken;
    }

    /**
     * @return The refresh token.
     */
    public String getToken() {
        return _refreshToken;
    }

    private static class Fields {
        private static final String REFRESH_TOKEN = "refresh_token";
    }
}
