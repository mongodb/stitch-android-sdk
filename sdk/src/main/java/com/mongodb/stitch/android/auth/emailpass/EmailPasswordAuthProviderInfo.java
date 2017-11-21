package com.mongodb.stitch.android.auth.emailpass;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongodb.stitch.android.auth.AuthProviderInfo;

/**
 * EmailPasswordAuthProviderInfo contains information needed to create
 * a {@link EmailPasswordAuthProvider}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmailPasswordAuthProviderInfo extends AuthProviderInfo {
    public static final String FQ_NAME = "local-userpass";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Config {
        static class Fields {
            static final String EMAIL_CONFIRMATION_URL = "emailConfirmationUrl";
            static final String RESET_PASSWORD_URL = "resetPasswordUrl";
        }

        private String _emailConfirmationUrl;
        private String _resetPasswordUrl;

        public Config(@JsonProperty(Fields.EMAIL_CONFIRMATION_URL) @NonNull
                      String emailConfirmationUrl,
                      @JsonProperty(Fields.RESET_PASSWORD_URL) @NonNull
                      String resetPasswordUrl) {
            this._emailConfirmationUrl = emailConfirmationUrl;
            this._resetPasswordUrl = resetPasswordUrl;
        }

        @JsonProperty(Fields.EMAIL_CONFIRMATION_URL)
        @NonNull
        public String getEmailConfirmationUrl() {
            return this._emailConfirmationUrl;
        }
        @JsonProperty(Fields.RESET_PASSWORD_URL)
        @NonNull
        public String getResetPasswordUrl() {
            return this._resetPasswordUrl;
        }
    }

    private Config _config;

    @JsonCreator
    public EmailPasswordAuthProviderInfo(@JsonProperty(AuthProviderInfo.Fields.TYPE) @Nullable final String type,
                                         @JsonProperty(AuthProviderInfo.Fields.NAME) @NonNull final String name,
                                         @JsonProperty("config") @NonNull final Config config) {
        super(type, name);
        this._config = config;
    }

    @JsonProperty("config")
    @NonNull
    public Config getConfig() {
        return this._config;
    }
}
