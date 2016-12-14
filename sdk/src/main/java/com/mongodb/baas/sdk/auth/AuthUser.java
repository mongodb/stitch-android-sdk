package com.mongodb.baas.sdk.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.bson.types.ObjectId;

import java.util.List;
import java.util.Map;

public class AuthUser {

    private final ObjectId _id;
    private final List<Identity> _identities;
    private final Map<String, Object> _data;

    @JsonCreator
    private AuthUser(
            @JsonProperty("_id")
            final ObjectId id,

            @JsonProperty("identities")
            final List<Identity> identities,

            @JsonProperty("data")
            final Map<String, Object> data
    ) {
        _id = id;
        _identities = identities;
        _data = data;
    }

    public static class Identity {

        private final String _id;
        private final String _provider;

        @JsonCreator
        public Identity(

                @JsonProperty("id")
                final String id,

                @JsonProperty("provider")
                final String provider
        ) {
            _id = id;
            _provider = provider;
        }

        @JsonProperty("id")
        public String getId() {
            return _id;
        }

        @JsonProperty("provider")
        public String getProvider() {
            return _provider;
        }
    }

    @JsonProperty("_id")
    public ObjectId getId() {
        return _id;
    }

    @JsonProperty("identities")
    public List<Identity> getIdentities() {
        return _identities;
    }

    @JsonProperty("data")
    public Map<String, Object> getData() {
        return _data;
    }
}
