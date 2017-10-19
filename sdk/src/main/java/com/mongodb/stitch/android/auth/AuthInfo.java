package com.mongodb.stitch.android.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AuthInfo represents a session on Stitch for a specific user.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthInfo {
    private static class Fields {
        private static final String ACCESS_TOKEN = "accessToken";
        private static final String USER_ID = "userId";
        private static final String PROVIDER = "provider";
        private static final String DEVICE_ID = "deviceId";
    }

    private final String _accessToken;
    private final String _userId;
    private final String _deviceId;
    private final String _provider;

    @JsonCreator
    private AuthInfo(
            @JsonProperty(Fields.ACCESS_TOKEN)
            final String accessToken,

            @JsonProperty(Fields.PROVIDER)
            final String provider,

            @JsonProperty(Fields.DEVICE_ID)
            final String deviceId,

            @JsonProperty(Fields.USER_ID)
            final String userId
    ) {
        _accessToken = accessToken;
        _userId = userId;
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
    @JsonProperty(value = Fields.USER_ID)
    public String getUserId() {
        return _userId;
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
    public AuthInfo withNewAccessToken(final String newAccessToken) {
        return new AuthInfo(newAccessToken, _provider, _deviceId, _userId);
    }
}
