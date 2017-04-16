package com.mongodb.baas.android.auth;

import org.bson.Document;

/**
 * An AuthProvider is responsible for providing the necessary information for a specific
 * authentication request.
 */
public interface AuthProvider {

    /**
     * @return The authentication type of this provider.
     */
    String getType();

    /**
     * @return The name of this provider.
     */
    String getName();

    /**
     * @return The JSON payload containing authentication material.
     */
    Document getAuthPayload();
}
