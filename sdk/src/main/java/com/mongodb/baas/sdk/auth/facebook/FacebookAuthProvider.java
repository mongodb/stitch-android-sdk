package com.mongodb.baas.sdk.auth.facebook;

import com.google.android.gms.tasks.RuntimeExecutionException;
import com.mongodb.baas.sdk.auth.AuthProvider;

import org.json.JSONException;
import org.json.JSONObject;

public class FacebookAuthProvider implements AuthProvider {
    private final String _accessToken;

    private FacebookAuthProvider(final String accessToken) {
        _accessToken = accessToken;
    }

    public static FacebookAuthProvider fromAccessToken(final String accessToken) {
        return new FacebookAuthProvider(accessToken);
    }

    @Override
    public String getType() {
        return "oauth2";
    }

    @Override
    public String getName() {
        return "facebook";
    }

    @Override
    public JSONObject getAuthPayload() {
        try {
            return new JSONObject().put("accessToken", _accessToken);
        } catch (final JSONException e) {
            throw new RuntimeExecutionException(e);
        }
    }
}
