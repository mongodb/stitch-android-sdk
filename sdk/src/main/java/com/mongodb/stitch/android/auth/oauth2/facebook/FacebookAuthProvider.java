package com.mongodb.stitch.android.auth.oauth2.facebook;

import com.mongodb.stitch.android.auth.AuthProvider;

import org.bson.Document;

import static com.mongodb.stitch.android.auth.oauth2.OAuth2.AUTH_TYPE;
import static com.mongodb.stitch.android.auth.oauth2.OAuth2.Fields;

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
    public Document getAuthPayload() {
        final Document payload = new Document();
        payload.put(Fields.ACCESS_TOKEN, _accessToken);
        return payload;
    }
}
