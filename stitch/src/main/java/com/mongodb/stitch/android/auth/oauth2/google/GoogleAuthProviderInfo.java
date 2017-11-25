package com.mongodb.stitch.android.auth.oauth2.google;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.android.gms.common.api.Scope;
import com.mongodb.stitch.android.auth.AuthProviderInfo;
import com.mongodb.stitch.android.auth.emailpass.EmailPasswordAuthProviderInfo;
import com.mongodb.stitch.android.auth.oauth2.facebook.FacebookAuthProviderInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * GoogleAuthProviderInfo contains information needed to create a {@link GoogleAuthProvider}.
 */
public class GoogleAuthProviderInfo extends AuthProviderInfo {
    public static final String FQ_NAME = "oauth2-google";
    private static final String METADATA_FIELDS = "metadata_fields";
    private static final String CONFIG = "config";

    public static class MetadataField {
        class Fields {
            static final String NAME = "name";
            static final String REQUIRED = "required";
        }

        private String _name;
        private boolean _required;

        private MetadataField(@JsonProperty(Fields.NAME)
                              @NonNull String name,
                              @JsonProperty(Fields.REQUIRED)
                                      boolean required) {
            this._name = name;
            this._required = required;
        }

        @JsonProperty(Fields.NAME)
        @NonNull
        public String getName() {
            return this._name;
        }

        @JsonProperty(Fields.REQUIRED)
        @NonNull
        public boolean getRequired() {
            return this._required;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Config {
        static class Fields {
            static final String CLIENT_ID = "clientId";
        }

        private String _clientId;

        public Config(@JsonProperty(Fields.CLIENT_ID)
                      @NonNull String clientId) {
            this._clientId = clientId;
        }

        @JsonProperty(Fields.CLIENT_ID)
        @NonNull
        public String getClientId() {
            return this._clientId;
        }
    }

    private List<MetadataField> _metadataFields;
    private Config _config;

    @JsonCreator
    public GoogleAuthProviderInfo(@JsonProperty(AuthProviderInfo.Fields.TYPE) @Nullable final String type,
                                  @JsonProperty(AuthProviderInfo.Fields.NAME) @NonNull final String name,
                                  @JsonProperty(CONFIG) @NonNull final Config config,
                                  @JsonProperty(METADATA_FIELDS) @NonNull final List<MetadataField> metadataFields) {
        super(type, name);
        this._config = config;
        this._metadataFields = metadataFields;
    }

    @JsonProperty(CONFIG)
    @NonNull
    public Config getConfig() {
        return this._config;
    }

    @JsonProperty(METADATA_FIELDS)
    @NonNull
    public List<MetadataField> getMetadataFields() {
        return this._metadataFields;
    }
}
