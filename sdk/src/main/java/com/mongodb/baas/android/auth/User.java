package com.mongodb.baas.android.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.bson.types.ObjectId;

import java.util.List;
import java.util.Map;

/**
 * User represents an authenticated user/
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class User {

    private final ObjectId _id;
    private final List<Identity> _identities;
    private final Map<String, Object> _data;

    @JsonCreator
    private User(
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

    /**
     * @return The Unique ID of this user within BaaS.
     */
    @JsonProperty("_id")
    public ObjectId getId() {
        return _id;
    }

    /**
     * @return The set of identities that this user is known by.
     */
    @JsonProperty("identities")
    public List<Identity> getIdentities() {
        return _identities;
    }

    /**
     * @return The extra data associated with this user.
     */
    @JsonProperty("data")
    public Map<String, Object> getData() {
        return _data;
    }

    /**
     * Identity is an alias by which this user can be authenticated in as.
     */
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

        /**
         * @return The provider specific Unique ID.
         */
        @JsonProperty("id")
        public String getId() {
            return _id;
        }

        /**
         * @return The provider of this identity.
         */
        @JsonProperty("provider")
        public String getProvider() {
            return _provider;
        }
    }
}
