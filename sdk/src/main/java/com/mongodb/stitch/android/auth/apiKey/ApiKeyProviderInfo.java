package com.mongodb.stitch.android.auth.apiKey;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * ApiKeyAuthProviderInfo contains information needed to create a {@link ApiKeyProvider}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiKeyProviderInfo {
    public static final String FQ_NAME = "api/key";
}
