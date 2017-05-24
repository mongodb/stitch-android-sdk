package com.mongodb.stitch.android.auth.oauth2.facebook;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * FacebookAuthProviderInfo contains information needed to create a {@link FacebookAuthProvider}.
 */
public class FacebookAuthProviderInfo {
    public static final String FQ_NAME = "oauth2/facebook";

    private final String _appId;
    private final List<String> _scopes;

    @JsonCreator
    private FacebookAuthProviderInfo(

            @JsonProperty(Fields.CLIENT_ID)
            final String appId,

            @JsonProperty(Fields.METADATA_FIELDS)
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

    private static class Fields {
        private static final String CLIENT_ID = "clientId";
        private static final String METADATA_FIELDS = "metadataFields";
    }
}
