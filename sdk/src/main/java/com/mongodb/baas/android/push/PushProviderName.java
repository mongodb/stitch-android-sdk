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

    private static final Map<String, PushProviderName> serviceNameToProvider = new HashMap<>();

    static {
        serviceNameToProvider.put(GCM._serviceName, GCM);
    }

    private final String _serviceName;

    PushProviderName(final String serviceName) {
        _serviceName = serviceName;
    }

    /**
     * @param serviceName The service claiming to be a provider.
     * @return The mapped provider name.
     */
    public static PushProviderName fromServiceName(final String serviceName) {
        if (!serviceNameToProvider.containsKey(serviceName)) {
            throw new IllegalArgumentException("Unknown push provider");
        }
        return serviceNameToProvider.get(serviceName);
    }

    /**
     * @return The reserved service name associated with this provider.
     */
    public String getServiceName() {
        return _serviceName;
    }
}
