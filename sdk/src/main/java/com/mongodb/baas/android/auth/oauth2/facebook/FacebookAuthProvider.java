package com.mongodb.baas.android.auth.oauth2.facebook;

import com.google.android.gms.tasks.RuntimeExecutionException;
import com.mongodb.baas.android.auth.AuthProvider;

import org.json.JSONException;
import org.json.JSONObject;

import static com.mongodb.baas.android.auth.oauth2.OAuth2.AUTH_TYPE;
import static com.mongodb.baas.android.auth.oauth2.OAuth2.Fields;

/**
 * FacebookAuthProvider provides a way to authenticate via Facebook's OAuth 2.0 provider.
 */
public class FacebookAuthProvider implements AuthProvider {

    private static final String AUTH_NAME = "facebook";

    private final String _accessToken;

    private FacebookAuthProvider(final String accessToken) {
        _accessToken = accessToken;
    }

    /**
     * Creates a {@link FacebookAuthProvider} from an access token from Facebook.
     *
     * @param accessToken The access token provided by Facebook.
     * @return The provider to authenticate with via Facebook.
     */
    public static FacebookAuthProvider fromAccessToken(final String accessToken) {
        return new FacebookAuthProvider(accessToken);
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
            return new JSONObject().put(Fields.ACCESS_TOKEN, _accessToken);
        } catch (final JSONException e) {
            throw new RuntimeExecutionException(e);
        }
    }
}
