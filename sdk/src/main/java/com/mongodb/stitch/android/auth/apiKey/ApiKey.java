package com.mongodb.stitch.android.auth.apiKey;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Api Key offers a secondary form of login.
 */
public final class ApiKey {
    private static class Fields {
        private static final String ID = "_id";
        private static final String KEY = "key";
        private static final String NAME = "name";
        private static final String DISABLED = "disabled";
    }

    private final String _id;
    private final String _key;
    private final String _name;
    private final boolean _disabled;

    @JsonCreator
    private ApiKey(@JsonProperty(Fields.ID)
                   @NonNull
                   final String id,
                   @JsonProperty(Fields.KEY)
                   @Nullable
                   final String key,
                   @JsonProperty(ApiKey.Fields.NAME)
                   @NonNull
                   final String name,
                   @JsonProperty(ApiKey.Fields.DISABLED)
                   final boolean disabled) {
        this._id = id;
        this._key = key;
        this._name = name;
        this._disabled = disabled;
    }

    @JsonProperty(Fields.ID)
    @NonNull
    public String getId() {
        return this._id;
    }

    @JsonProperty(Fields.ID)
    @Nullable
    public String getKey() {
        return this._key;
    }

    @JsonProperty(Fields.ID)
    @NonNull
    public String getName() {
        return this._name;
    }

    @JsonProperty(Fields.ID)
    public boolean getDisabled() {
        return this._disabled;
    }
}
