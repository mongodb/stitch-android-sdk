package com.mongodb.stitch.android.auth.anonymous;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongodb.stitch.android.auth.AuthProviderInfo;

/**
 * AnonymousAuthProviderInfo contains information needed to create a {@link AnonymousAuthProvider}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnonymousAuthProviderInfo extends AuthProviderInfo {
    public static final String FQ_NAME = "anon/user";

    @JsonCreator
    public AnonymousAuthProviderInfo(@JsonProperty(Fields.TYPE) @Nullable final String type,
                                     @JsonProperty(Fields.NAME) @NonNull final String name) {
        super(type, name);
    }
}
