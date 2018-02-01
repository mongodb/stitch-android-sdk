package com.mongodb.stitch.android.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * LinkInfo represents the Stitch server's response for a link request.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LinkInfo {
    private static class Fields {
        private static final String USER_ID = "user_id";
    }

    private final String _userId;

    @JsonCreator
    private LinkInfo(
            @JsonProperty(Fields.USER_ID)
            final String userId
    ) {
        _userId = userId;
    }

    /**
     * @return The user this session was created for.
     */
    @JsonProperty(value = Fields.USER_ID)
    public String getUserId() {
        return _userId;
    }
}