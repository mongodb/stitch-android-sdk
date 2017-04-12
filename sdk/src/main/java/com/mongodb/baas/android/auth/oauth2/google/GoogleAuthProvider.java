package com.mongodb.baas.android.auth.oauth2.google;

import com.google.android.gms.tasks.RuntimeExecutionException;
import com.mongodb.baas.android.auth.AuthProvider;

import org.json.JSONException;
import org.json.JSONObject;

import static com.mongodb.baas.android.auth.oauth2.OAuth2.AUTH_TYPE;
import static com.mongodb.baas.android.auth.oauth2.OAuth2.Fields;

/**
 * GoogleAuthProvider provides a way to authenticate via Google's OAuth 2.0 provider.
 */
public class GoogleAuthProvider implements AuthProvider {


    private static final String AUTH_NAME = "google";

    private final String _authCode;

    private GoogleAuthProvider(final String authCode) {
        _authCode = authCode;
    }

    /**
     * Creates a {@link GoogleAuthProvider} from an auth code from Google.
     *
     * @param authCode The auth code provided by Google.
     * @return The provider to authenticate with via Google.
     */
    public static GoogleAuthProvider fromAuthCode(final String authCode) {
        return new GoogleAuthProvider(authCode);
    }

    @Override
    public String getType() {
        return AUTH_TYPE;
    }

    @Override
    public String getName() {
        return AUTH_NAME;
    }

    @Override
    public JSONObject getAuthPayload() {
        try {
            return new JSONObject().put(Fields.AUTH_CODE, _authCode);
        } catch (final JSONException e) {
            throw new RuntimeExecutionException(e);
        }
    }
}
