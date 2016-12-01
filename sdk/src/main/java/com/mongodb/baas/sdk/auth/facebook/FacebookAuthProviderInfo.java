package com.mongodb.baas.sdk.auth.facebook;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class FacebookAuthProviderInfo {
    public static final String FQ_NAME = "oauth2/facebook";

    private final String _appId;
    private final List<String> _scopes;

    @JsonCreator
    private FacebookAuthProviderInfo(

            @JsonProperty("clientId")
            final String appId,

            @JsonProperty("metadataFields")
            final List<String> scopes
    ) {
        _appId = appId;
        _scopes = scopes;
    }

    public String getApplicationId() {
        return _appId;
    }

    public List<String> getScopes() {
        return _scopes;
    }
}
