package com.mongodb.baas.android.auth.anonymous;

import com.mongodb.baas.android.auth.AuthProvider;

import org.json.JSONObject;

/**
 * AnonymousAuthProvider provides a way to authenticate anonymously.
 */
public class AnonymousAuthProvider implements AuthProvider {

    private static final String AUTH_TYPE = "anon";
    private static final String AUTH_NAME = "user";

    @Override
    public String getType() {
        return AUTH_TYPE;
    }

    @Override
    public String getName() {
        return AUTH_NAME;
    }

    @Override
    public JSONObject getAuthPayload() {
        return new JSONObject();
    }
}
