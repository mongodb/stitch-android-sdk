package com.mongodb.stitch.android.auth.emailpass;

import android.support.annotation.NonNull;

import com.mongodb.stitch.android.auth.AuthProvider;

import org.bson.Document;

/**
 * EmailPasswordAuthProvider provides a way to authenticate using an email and password.
 */
public class EmailPasswordAuthProvider implements AuthProvider {
    private static final String AUTH_TYPE = "local";
    private static final String AUTH_NAME = "userpass";

    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";

    private final String email;
    private final String password;

    public EmailPasswordAuthProvider(@NonNull String email, @NonNull String password) {
        this.email = email;
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public String getName() {
        return AUTH_NAME;
    }

    @Override
    public String getType() {
        return AUTH_TYPE;
    }

    @Override
    public Document getAuthPayload() {
        Document document = new Document();

        document.put(KEY_USERNAME, this.email);
        document.put(KEY_PASSWORD, this.password);

        return document;
    }
}
