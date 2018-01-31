package com.mongodb.stitch.android.auth.anonymous;

import com.mongodb.stitch.android.auth.AuthProvider;

import org.bson.Document;

/**
 * AnonymousAuthProvider provides a way to authenticate anonymously.
 */
public class AnonymousAuthProvider implements AuthProvider {

    public static final String AUTH_TYPE = "anon-user";

    @Override
    public String getType() {
        return AUTH_TYPE;
    }

    @Override
    public Document getAuthPayload() {
        return new Document();
    }
}
