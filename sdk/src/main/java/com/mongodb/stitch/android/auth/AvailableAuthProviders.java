package com.mongodb.stitch.android.auth;

import android.support.annotation.Nullable;

import com.mongodb.stitch.android.auth.anonymous.AnonymousAuthProviderInfo;
import com.mongodb.stitch.android.auth.emailpass.EmailPasswordAuthProviderInfo;
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
    private final EmailPasswordAuthProviderInfo _emailPass;

    public AvailableAuthProviders() {
        this(null, null, null, null);
    }

    public AvailableAuthProviders(
            final FacebookAuthProviderInfo fb,
            final GoogleAuthProviderInfo google,
            final AnonymousAuthProviderInfo anon,
            final EmailPasswordAuthProviderInfo emailPass
    ) {
        _fb = fb;
        _google = google;
        _anon = anon;
        _emailPass = emailPass;
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
    @Nullable
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
    @Nullable
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
    @Nullable
    public AnonymousAuthProviderInfo getAnonymous() {
        return _anon;
    }

    /**
     * @return Whether or not the app supports email/password authentication.
     */
    public boolean hasEmailPassword() {
        return _emailPass != null;
    }

    /**
     * @return The email/password provider information.
     */
    @Nullable
    public EmailPasswordAuthProviderInfo getEmailPassword() {
        return _emailPass;
    }

    public static class Builder {
        private FacebookAuthProviderInfo _fb;
        private GoogleAuthProviderInfo _google;
        private AnonymousAuthProviderInfo _anon;
        private EmailPasswordAuthProviderInfo _emailPass;

        public AvailableAuthProviders build() {
            return new AvailableAuthProviders(_fb, _google, _anon, _emailPass);
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

        public void withEmailPass(final EmailPasswordAuthProviderInfo emailPassAuthProv) {
            _emailPass = emailPassAuthProv;
        }
    }
}
