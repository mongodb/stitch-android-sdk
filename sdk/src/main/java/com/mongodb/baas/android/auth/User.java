package com.mongodb.baas.android.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.bson.types.ObjectId;

import java.util.List;
import java.util.Map;

/**
 * User represents an authenticated user.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class User {

    private final String _id;
    private final List<Identity> _identities;
    private final Map<String, Object> _data;

    @JsonCreator
    private User(
            @JsonProperty(Fields.ID)
            final String id,

            @JsonProperty(Fields.IDENTITIES)
            final List<Identity> identities,

            @JsonProperty(Fields.DATA)
            final Map<String, Object> data
    ) {
        _id = id;
        _identities = identities;
        _data = data;
    }

    /**
     * @return The Unique ID of this user within BaaS.
     */
    @JsonProperty(Fields.ID)
    public String getId() {
        return _id;
    }

    /**
     * @return The set of identities that this user is known by.
     */
    @JsonProperty(Fields.IDENTITIES)
    public List<Identity> getIdentities() {
        return _identities;
    }

    /**
     * @return The extra data associated with this user.
     */
    @JsonProperty(Fields.DATA)
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

                @JsonProperty(Fields.ID)
                final String id,

                @JsonProperty(Fields.PROVIDER)
                final String provider
        ) {
            _id = id;
            _provider = provider;
        }

        /**
         * @return The provider specific Unique ID.
         */
        @JsonProperty(Fields.ID)
        public String getId() {
            return _id;
        }

        /**
         * @return The provider of this identity.
         */
        @JsonProperty(Fields.PROVIDER)
        public String getProvider() {
            return _provider;
        }

        private static class Fields {
            private static final String ID = "id";
            private static final String PROVIDER = "provider";
        }
    }

    private static class Fields {
        private static final String ID = "_id";
        private static final String IDENTITIES = "identities";
        private static final String DATA = "data";
    }
}
