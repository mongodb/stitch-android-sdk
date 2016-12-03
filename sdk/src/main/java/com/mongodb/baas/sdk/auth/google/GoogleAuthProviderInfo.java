package com.mongodb.baas.sdk.auth.google;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.android.gms.common.api.Scope;

import java.util.ArrayList;
import java.util.List;

public class GoogleAuthProviderInfo {
    public static final String FQ_NAME = "oauth2/google";

    private final String _clientId;
    private final List<Scope> _scopes;

    @JsonCreator
    private GoogleAuthProviderInfo(

            @JsonProperty("clientId")
            final String clientId,

            @JsonProperty("metadataFields")
            final List<String> scopes
    ) {
        _clientId = clientId;

        final List<Scope> gScopes = new ArrayList<>(scopes.size());
        for (final String scope : scopes) {
            gScopes.add(new Scope(scope));
        }
        _scopes = gScopes;
    }

    public String getClientId() {
        return _clientId;
    }

    public List<Scope> getScopes() {
        return _scopes;
    }
}
