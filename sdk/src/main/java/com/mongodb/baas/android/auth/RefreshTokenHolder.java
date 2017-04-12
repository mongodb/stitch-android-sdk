package com.mongodb.baas.android.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RefreshTokenHolder is a container for the serialization of refresh tokens.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RefreshTokenHolder {

    @JsonProperty("refreshToken")
    private final String _refreshToken;

    @JsonCreator
    private RefreshTokenHolder(
            @JsonProperty("refreshToken")
            final String refreshToken
    ) {
        _refreshToken = refreshToken;
    }

    public String getToken() {
        return _refreshToken;
    }
}
