package com.mongodb.baas.sdk.auth.google;

import com.google.android.gms.tasks.RuntimeExecutionException;
import com.mongodb.baas.sdk.auth.AuthProvider;

import org.json.JSONException;
import org.json.JSONObject;

public class GoogleAuthProvider implements AuthProvider {
    private final String _authCode;

    private GoogleAuthProvider(final String authCode) {
        _authCode = authCode;
    }

    public static GoogleAuthProvider fromIdToken(final String authCode) {
        return new GoogleAuthProvider(authCode);
    }

    @Override
    public String getType() {
        return "oauth2";
    }

    @Override
    public String getName() {
        return "google";
    }

    @Override
    public JSONObject getAuthPayload() {
        try {
            return new JSONObject().put("authCode", _authCode);
        } catch (final JSONException e) {
            throw new RuntimeExecutionException(e);
        }
    }
}
