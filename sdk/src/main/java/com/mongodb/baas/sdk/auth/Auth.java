package com.mongodb.baas.sdk.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Auth {

    private final String _accessToken;
    private final AuthUser _authUser;
    private final String _provider;

    @JsonCreator
    private Auth(
            @JsonProperty("accessToken")
            final String accessToken,

            @JsonProperty("user")
            final AuthUser authUser,

            @JsonProperty("provider")
            final String provider
    ) {
        _accessToken = accessToken;
        _authUser = authUser;
        _provider = provider;
    }

    @JsonProperty("accessToken")
    public String getAccessToken() {
        return _accessToken;
    }

    @JsonProperty("user")
    public AuthUser getUser() {
        return _authUser;
    }

    @JsonProperty("provider")
    public String getProvider() {
        return _provider;
    }

    public Auth withNewAccessToken(final String newAccessToken) {
        return new Auth(newAccessToken, _authUser, _provider);
    }
}
