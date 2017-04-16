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
    private final User _user;
    private final String _provider;
    private final String _deviceId;

    @JsonCreator
    private Auth(
            @JsonProperty(Fields.ACCESS_TOKEN)
            final String accessToken,

            @JsonProperty(Fields.USER)
            final User user,

            @JsonProperty(Fields.PROVIDER)
            final String provider,

            @JsonProperty(Fields.DEVICE_ID)
            final String deviceId
    ) {
        _accessToken = accessToken;
        _user = user;
        _provider = provider;
        _deviceId = deviceId;
    }

    /**
     * @return The current access token for this session.
     */
    @JsonProperty(Fields.ACCESS_TOKEN)
    public String getAccessToken() {
        return _accessToken;
    }

    /**
     * @return The user this session was created for.
     */
    @JsonProperty(Fields.USER)
    public User getUser() {
        return _user;
    }

    /**
     * @return The provider that authenticated this session.
     */
    @JsonProperty(Fields.PROVIDER)
    public String getProvider() {
        return _provider;
    }

    /**
     * @return The user this session was created for.
     */
    @JsonProperty(Fields.DEVICE_ID)
    public String getDeviceId() {
        return _deviceId;
    }

    /**
     * @param newAccessToken The new access token to use.
     * @return A new session with a fresh access token.
     */
    public Auth withNewAccessToken(final String newAccessToken) {
        return new Auth(newAccessToken, _user, _provider, _deviceId);
    }

    private static class Fields {
        private static final String ACCESS_TOKEN = "accessToken";
        private static final String USER = "user";
        private static final String PROVIDER = "provider";
        private static final String DEVICE_ID = "deviceId";
    }
}
