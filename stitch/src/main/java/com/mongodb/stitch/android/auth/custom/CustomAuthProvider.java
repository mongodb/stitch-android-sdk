package com.mongodb.stitch.android.auth.custom;

import android.support.annotation.NonNull;

import com.mongodb.stitch.android.StitchClient;
import com.mongodb.stitch.android.auth.AuthProvider;

import org.bson.Document;

public class CustomAuthProvider implements AuthProvider {
    public static final String AUTH_TYPE = "custom-token";

    private static final String KEY_TOKEN = "token";

    private final String jwt;

    public CustomAuthProvider(@NonNull String jwt) {
        this.jwt = jwt;
    }

    public String getToken() {
        return jwt;
    }

    @Override
    public String getType() {
        return AUTH_TYPE;
    }

    @Override
    public Document getAuthPayload() {
        return new Document(KEY_TOKEN, this.jwt);
    }
}
