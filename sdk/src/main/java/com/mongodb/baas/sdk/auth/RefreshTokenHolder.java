package com.mongodb.baas.sdk.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

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
