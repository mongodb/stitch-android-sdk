package com.mongodb.stitch.android.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * UserProfile represents an authenticated user.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserProfile {

    private final String _userId;
    private final List<Identity> _identities;
    private final Map<String, Object> _data;

    @JsonCreator
    private UserProfile(
            @JsonProperty(Fields.ID)
            final String userId,

            @JsonProperty(Fields.IDENTITIES)
            final List<Identity> identities,

            @JsonProperty(Fields.DATA)
            final Map<String, Object> data
    ) {
        _userId = userId;
        _identities = identities;
        _data = data;
    }

    /**
     * @return The Unique ID of this user within Stitch.
     */
    @JsonProperty(Fields.ID)
    public String getId() {
        return _userId;
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
        private final String _provider_id;

        @JsonCreator
        public Identity(
                @JsonProperty(Fields.ID)
                final String id,

                @JsonProperty(Fields.PROVIDER)
                final String provider,

                @JsonProperty(Fields.PROVIDER_ID)
                final String provider_id
        ) {
            _id = id;
            _provider = provider;
            _provider_id = provider_id;
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

        /**
         * @return The ID of the provider of this identity.
         */
        @JsonProperty(Fields.PROVIDER_ID)
        public String getProviderID() {
            return _provider_id;
        }

        private static class Fields {
            private static final String ID = "id";
            private static final String PROVIDER = "provider_type";
            private static final String PROVIDER_ID = "provider_id";
        }
    }

    private static class Fields {
        private static final String ID = "userId";
        private static final String IDENTITIES = "identities";
        private static final String DATA = "data";
    }
}
