package com.mongodb.stitch.android.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AuthInfo represents a session on Stitch for a specific user.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthInfo {
    private static class Fields {
        private static final String ACCESS_TOKEN = "access_token";
        private static final String USER_ID = "user_id";
        private static final String DEVICE_ID = "device_id";
    }

    private final String _accessToken;
    private final String _userId;
    private final String _deviceId;

    @JsonCreator
    private AuthInfo(
            @JsonProperty(Fields.ACCESS_TOKEN)
            final String accessToken,

            @JsonProperty(Fields.DEVICE_ID)
            final String deviceId,

            @JsonProperty(Fields.USER_ID)
            final String userId
    ) {
        _accessToken = accessToken;
        _userId = userId;
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
        return new AuthInfo(newAccessToken, _deviceId, _userId);
    }
}
