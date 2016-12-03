package com.mongodb.baas.sdk.auth;

import com.mongodb.baas.sdk.auth.facebook.FacebookAuthProviderInfo;
import com.mongodb.baas.sdk.auth.google.GoogleAuthProviderInfo;

public class AuthProviderInfo {
    private final FacebookAuthProviderInfo _fb;
    private final GoogleAuthProviderInfo _google;

    public AuthProviderInfo() {
        this(null, null);
    }

    public AuthProviderInfo(
            final FacebookAuthProviderInfo fb,
            final GoogleAuthProviderInfo google
    ) {
        _fb = fb;
        _google = google;
    }

    public boolean hasFacebook() {
        return _fb != null;
    }

    public FacebookAuthProviderInfo getFacebook() {
        return _fb;
    }

    public boolean hasGoogle() {
        return _google != null;
    }

    public GoogleAuthProviderInfo getGoogle() {
        return _google;
    }

    public static class Builder {
        private FacebookAuthProviderInfo _fb;
        private GoogleAuthProviderInfo _google;

        public AuthProviderInfo build() {
            return new AuthProviderInfo(_fb, _google);
        }

        public void withFacebook(final FacebookAuthProviderInfo fbAuthProv) {
            _fb = fbAuthProv;
        }

        public void withGoogle(final GoogleAuthProviderInfo googleAuthProv) {
            _google = googleAuthProv;
        }
    }
}
