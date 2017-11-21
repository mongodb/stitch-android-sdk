package com.mongodb.stitch.android.auth;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by jasonflax on 11/20/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class AuthProviderInfo {
    public static class Fields {
        public static final String ID = "_id";
        public static final String TYPE = "type";
        public static final String NAME = "name";
        public static final String DISABLED = "disabled";
    }

    private String _id;
    private String _name;
    private String _type;
    private boolean _disabled;

    @JsonCreator
    protected AuthProviderInfo(@JsonProperty(Fields.ID) @NonNull final String id,
                               @JsonProperty(Fields.TYPE) @Nullable final String type,
                               @JsonProperty(Fields.NAME) @NonNull final String name,
                               @JsonProperty(Fields.DISABLED) final boolean disabled) {
        this._id = id;
        this._type = type;
        this._name = name;
        this._disabled = disabled;
    }

    @JsonProperty(Fields.ID)
    @NonNull
    public String getId() {
        return this._id;
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

    @JsonProperty(Fields.DISABLED)
    public boolean getDisabled() {
        return this._disabled;
    }
}
