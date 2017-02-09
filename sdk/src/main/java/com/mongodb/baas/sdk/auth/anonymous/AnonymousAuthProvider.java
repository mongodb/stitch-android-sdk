package com.mongodb.baas.sdk.auth.anonymous;

import com.mongodb.baas.sdk.auth.AuthProvider;

import org.json.JSONObject;

public class AnonymousAuthProvider implements AuthProvider {

    @Override
    public String getType() {
        return "anon";
    }

    @Override
    public String getName() {
        return "user";
    }

    @Override
    public JSONObject getAuthPayload() {
        return new JSONObject();
    }
}
