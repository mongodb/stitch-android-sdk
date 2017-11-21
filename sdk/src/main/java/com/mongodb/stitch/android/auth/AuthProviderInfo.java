package com.mongodb.stitch.android.auth;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class AuthProviderInfo {
    public static class Fields {
        public static final String TYPE = "type";
        public static final String NAME = "name";
    }

    private String _name;
    private String _type;

    @JsonCreator
    protected AuthProviderInfo(@JsonProperty(Fields.TYPE) @Nullable final String type,
                               @JsonProperty(Fields.NAME) @NonNull final String name) {
        this._type = type;
        this._name = name;
    }

    @JsonProperty(Fields.TYPE)
    @Nullable
    public String getType() {
        return this._type;
    }

    @JsonProperty(Fields.NAME)
    @NonNull
    public String getName() {
        return this._name;
    }
}
