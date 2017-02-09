package com.mongodb.baas.sdk.auth;

import com.mongodb.baas.sdk.auth.anonymous.AnonymousAuthProviderInfo;
import com.mongodb.baas.sdk.auth.facebook.FacebookAuthProviderInfo;
import com.mongodb.baas.sdk.auth.google.GoogleAuthProviderInfo;

public class AuthProviderInfo {
    private final FacebookAuthProviderInfo _fb;
    private final GoogleAuthProviderInfo _google;
    private final AnonymousAuthProviderInfo _anon;

    public AuthProviderInfo() {
        this(null, null, null);
    }

    public AuthProviderInfo(
            final FacebookAuthProviderInfo fb,
            final GoogleAuthProviderInfo google,
            final AnonymousAuthProviderInfo anon
    ) {
        _fb = fb;
        _google = google;
        _anon = anon;
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

    public boolean hasAnonymous() {
        return _anon != null;
    }

    public AnonymousAuthProviderInfo getAnonymous() {
        return _anon;
    }

    public static class Builder {
        private FacebookAuthProviderInfo _fb;
        private GoogleAuthProviderInfo _google;
        private AnonymousAuthProviderInfo _anon;

        public AuthProviderInfo build() {
            return new AuthProviderInfo(_fb, _google, _anon);
        }

        public void withFacebook(final FacebookAuthProviderInfo fbAuthProv) {
            _fb = fbAuthProv;
        }

        public void withGoogle(final GoogleAuthProviderInfo googleAuthProv) {
            _google = googleAuthProv;
        }

        public void withAnonymous(final AnonymousAuthProviderInfo anonAuthProv) {
            _anon = anonAuthProv;
        }
    }
}
