package com.mongodb.baas.sdk.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Auth {
    @JsonProperty("token")
    private final String _token;

    @JsonProperty("user")
    private final AuthUser _authUser;

    @JsonProperty("provider")
    private final String _provider;

    @JsonCreator
    private Auth(
            @JsonProperty("token")
            final String token,

            @JsonProperty("user")
            final AuthUser authUser,

            @JsonProperty("provider")
            final String provider
    ) {
        _token = token;
        _authUser = authUser;
        _provider = provider;
    }

    public String getToken() {
        return _token;
    }

    public AuthUser getUser() {
        return _authUser;
    }

    public String getProvider() {
        return _provider;
    }
}
