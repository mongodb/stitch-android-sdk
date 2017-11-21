package com.mongodb.stitch.android.auth.emailpass;

import android.support.annotation.NonNull;

import com.mongodb.stitch.android.auth.AuthProvider;
import com.mongodb.stitch.android.StitchClient;

import org.bson.Document;

/**
 * EmailPasswordAuthProvider provides a way to authenticate using an email and password.
 */
public class EmailPasswordAuthProvider implements AuthProvider {
    private static final String AUTH_TYPE = "local-userpass";
    private static final String AUTH_NAME = "local-userpass";

    private static final String KEY_USERNAME = "username";
    private static final String KEY_EMAIL = "email";
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

    /**
     * Create a payload exclusively for {@link StitchClient#register(String, String)}
     *
     * @return a payload containing the email and password of the registrant
     */
    public Document getRegistrationPayload() {
        final Document doc = new Document();

        doc.put(KEY_EMAIL, this.email);
        doc.put(KEY_PASSWORD, this.password);

        return doc;
    }

    @Override
    public Document getAuthPayload() {
        final Document document = new Document();

        document.put(KEY_USERNAME, this.email);
        document.put(KEY_PASSWORD, this.password);

        return document;
    }
}
