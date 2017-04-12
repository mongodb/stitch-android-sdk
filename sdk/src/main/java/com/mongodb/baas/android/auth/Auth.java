package com.mongodb.baas.android.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Auth represents a session on BaaS for a specific user.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Auth {

    private final String _accessToken;
    private final User _User;
    private final String _provider;

    @JsonCreator
    private Auth(
            @JsonProperty("accessToken")
            final String accessToken,

            @JsonProperty("user")
            final User user,

            @JsonProperty("provider")
            final String provider
    ) {
        _accessToken = accessToken;
        _User = user;
        _provider = provider;
    }

    /**
     * @return The current access token for this session.
     */
    @JsonProperty("accessToken")
    public String getAccessToken() {
        return _accessToken;
    }

    /**
     * @return The user this session was created for.
     */
    @JsonProperty("user")
    public User getUser() {
        return _User;
    }

    /**
     * @return The provider that authenticated this session.
     */
    @JsonProperty("provider")
    public String getProvider() {
        return _provider;
    }

    /**
     * @param newAccessToken The new access token to use.
     * @return A new session with a fresh access token.
     */
    public Auth withNewAccessToken(final String newAccessToken) {
        return new Auth(newAccessToken, _User, _provider);
    }
}
