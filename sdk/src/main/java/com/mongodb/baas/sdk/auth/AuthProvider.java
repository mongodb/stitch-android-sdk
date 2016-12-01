package com.mongodb.baas.sdk.auth;

import org.json.JSONObject;

public interface AuthProvider {
    String getType();
    String getName();
    JSONObject getAuthPayload();
}
