package com.mongodb.stitch.android.auth.anonymous;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * AnonymousAuthProviderInfo contains information needed to create a {@link AnonymousAuthProvider}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnonymousAuthProviderInfo {
    public static final String FQ_NAME = "anon/user";
}
