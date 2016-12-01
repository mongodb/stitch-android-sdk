package com.mongodb.baas.sdk.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.bson.types.ObjectId;

import java.util.List;
import java.util.Map;

public class AuthUser {
    @JsonProperty("_id")
    private final ObjectId _id;

    @JsonProperty("identities")
    private final List<Identity> _identities;

    @JsonProperty("data")
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

        @JsonProperty("id")
        private final String _id;

        @JsonProperty("provider")
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

        public String getId() {
            return _id;
        }

        public String getProvider() {
            return _provider;
        }
    }

    public ObjectId getId() {
        return _id;
    }

    public List<Identity> getIdentities() {
        return _identities;
    }

    public Map<String, Object> getData() {
        return _data;
    }
}
