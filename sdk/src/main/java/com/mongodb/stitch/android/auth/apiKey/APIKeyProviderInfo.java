package com.mongodb.stitch.android.auth.apiKey;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongodb.stitch.android.auth.AuthProviderInfo;

/**
 * ApiKeyAuthProviderInfo contains information needed to create a {@link APIKeyProvider}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class APIKeyProviderInfo extends AuthProviderInfo {
    public static final String FQ_NAME = "api-key";

    @JsonCreator
    public APIKeyProviderInfo(@JsonProperty(Fields.TYPE) @Nullable final String type,
                              @JsonProperty(Fields.NAME) @NonNull final String name) {
        super(type, name);
    }
}
