package com.mongodb.stitch.android;

/**
 * An AuthListener provides an observer interface for users to listen in on auth
 * events from a {@link StitchClient}.
 */
public interface AuthListener {

    /**
     * Called when a user is logged in.
     */
    void onLogin();

    /**
     * Called when a user is logged out.
     */
    void onLogout();
}
