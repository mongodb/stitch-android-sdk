package com.mongodb.baas.android.auth.oauth2.google;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.android.gms.common.api.Scope;

import java.util.ArrayList;
import java.util.List;

/**
 * GoogleAuthProviderInfo contains information needed to create a {@link GoogleAuthProvider}.
 */
public class GoogleAuthProviderInfo {
    public static final String FQ_NAME = "oauth2/google";

    private final String _clientId;
    private final List<Scope> _scopes;

    @JsonCreator
    private GoogleAuthProviderInfo(

            @JsonProperty(Fields.CLIENT_ID)
            final String clientId,

            @JsonProperty(Fields.METADATA_FIELDS)
            final List<String> scopes
    ) {
        _clientId = clientId;

        if (scopes == null) {
            _scopes = new ArrayList<>();
            return;
        }

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

    private static class Fields {
        private static final String CLIENT_ID = "clientId";
        private static final String METADATA_FIELDS = "metadataFields";
    }
}
