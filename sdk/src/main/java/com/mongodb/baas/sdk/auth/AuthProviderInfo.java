package com.mongodb.baas.sdk.auth;

import com.mongodb.baas.sdk.auth.facebook.FacebookAuthProviderInfo;

public class AuthProviderInfo {
    private final FacebookAuthProviderInfo _fb;

    public AuthProviderInfo() {
        this(null);
    }

    public AuthProviderInfo(final FacebookAuthProviderInfo fb) {
        _fb = fb;
    }

    public boolean hasFacebook() {
        return _fb != null;
    }

    public FacebookAuthProviderInfo getFacebook() {
        return _fb;
    }

    public static class Builder {
        private FacebookAuthProviderInfo _fb;

        public AuthProviderInfo build() {
            return new AuthProviderInfo(_fb);
        }

        public void withFacebook(final FacebookAuthProviderInfo fbAuthProv) {
            _fb = fbAuthProv;
        }
    }
}
