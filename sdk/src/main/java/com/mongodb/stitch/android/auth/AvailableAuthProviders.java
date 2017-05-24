package com.mongodb.stitch.android.auth;

import com.mongodb.stitch.android.auth.anonymous.AnonymousAuthProviderInfo;
import com.mongodb.stitch.android.auth.oauth2.facebook.FacebookAuthProviderInfo;
import com.mongodb.stitch.android.auth.oauth2.google.GoogleAuthProviderInfo;

/**
 * AvailableAuthProviders is a collection of available auth providers for an app and the information
 * needed to utilize them.
 */
public class AvailableAuthProviders {
    private final FacebookAuthProviderInfo _fb;
    private final GoogleAuthProviderInfo _google;
    private final AnonymousAuthProviderInfo _anon;

    public AvailableAuthProviders() {
        this(null, null, null);
    }

    public AvailableAuthProviders(
            final FacebookAuthProviderInfo fb,
            final GoogleAuthProviderInfo google,
            final AnonymousAuthProviderInfo anon
    ) {
        _fb = fb;
        _google = google;
        _anon = anon;
    }

    /**
     * @return Whether or not the app supports authentication via Facebook OAuth 2.0.
     */
    public boolean hasFacebook() {
        return _fb != null;
    }

    /**
     * @return The Facebook OAuth 2.0 information.
     */
    public FacebookAuthProviderInfo getFacebook() {
        return _fb;
    }

    /**
     * @return Whether or not the app supports authentication via Google OAuth 2.0.
     */
    public boolean hasGoogle() {
        return _google != null;
    }

    /**
     * @return The Google OAuth 2.0 information.
     */
    public GoogleAuthProviderInfo getGoogle() {
        return _google;
    }

    /**
     * @return Whether or not the app supports anonymous authentication.
     */
    public boolean hasAnonymous() {
        return _anon != null;
    }

    /**
     * @return The anonymous provider information.
     */
    public AnonymousAuthProviderInfo getAnonymous() {
        return _anon;
    }

    public static class Builder {
        private FacebookAuthProviderInfo _fb;
        private GoogleAuthProviderInfo _google;
        private AnonymousAuthProviderInfo _anon;

        public AvailableAuthProviders build() {
            return new AvailableAuthProviders(_fb, _google, _anon);
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
