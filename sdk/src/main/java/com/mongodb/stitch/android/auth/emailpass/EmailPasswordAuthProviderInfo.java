package com.mongodb.stitch.android.auth.emailpass;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * EmailPasswordAuthProviderInfo contains information needed to create
 * a {@link EmailPasswordAuthProvider}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmailPasswordAuthProviderInfo {
    public static final String FQ_NAME = "local/userpass";
}
