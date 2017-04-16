package com.mongodb.baas.android.push;

import java.util.HashMap;
import java.util.Map;

/**
 * PushProviderNames are the set of reserved push providers and their respective services.
 */
public enum PushProviderName {

    /**
     * Google Cloud Messaging - Currently accessed via the gcm service.
     */
    GCM("gcm");

    private static final Map<String, PushProviderName> typeNameToProvider = new HashMap<>();

    static {
        typeNameToProvider.put(GCM._typeName, GCM);
    }

    private final String _typeName;

    /**
     * @param typeName The type identifier for the provider.
     */
    PushProviderName(final String typeName) {
        _typeName = typeName;
    }

    /**
     * @param typeName The type claiming to be a provider.
     * @return The mapped provider name.
     */
    public static PushProviderName fromTypeName(final String typeName) {
        if (!typeNameToProvider.containsKey(typeName)) {
            throw new IllegalArgumentException("Unknown push provider");
        }
        return typeNameToProvider.get(typeName);
    }

    /**
     * @return The reserved service name associated with this provider.
     */
    public String getTypeName() {
        return _typeName;
    }
}
