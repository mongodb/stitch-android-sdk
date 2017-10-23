package com.mongodb.stitch.android.auth;

import android.util.Base64;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.stitch.android.StitchException;

import java.io.IOException;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class DecodedJWT {

    private final String[] parts;
    private final Map<String, Object> payload;

    public DecodedJWT(final String jwt) {
        parts = splitToken(jwt);
        final String payloadJson = new String(Base64.decode(parts[1], Base64.URL_SAFE));
        payload = convertFromJSON(payloadJson);
    }

    public Long getExpiration() throws StitchException {
        if (payload.get("exp") instanceof Number) {
            return ((Number)payload.get("exp")).longValue();
        }
        throw new StitchException.StitchRequestException("Malformed JWT token. The expiration field must be a number.");
    }

    public String getName() throws StitchException {
        if (payload.get("name") instanceof String) {
            return (String)payload.get("name");
        }
        throw new StitchException.StitchRequestException("Malformed JWT token. The name field must be a string.");
    }

    private String[] splitToken(final String jwt) {
        final String[] parts = jwt.split("\\.");
        if (parts.length != 3) {
            throw new StitchException.StitchRequestException(String.format("Malformed JWT token. The string %s should have 3 parts.", jwt));
        }
        return parts;
    }

    private Map<String, Object> convertFromJSON(final String json) throws StitchException {
        if (json == null) {
            throw exceptionForInvalidJson(null);
        }
        try {
            return new ObjectMapper().readValue(json, Map.class);
        } catch (IOException e) {
            throw exceptionForInvalidJson(json);
        }
    }

    private StitchException exceptionForInvalidJson(String json) {
        return new StitchException.StitchRequestException(String.format("The string '%s' doesn't have a valid JSON format.", json));
    }
}
