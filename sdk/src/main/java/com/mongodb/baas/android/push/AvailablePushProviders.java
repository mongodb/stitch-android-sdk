package com.mongodb.baas.android.push;

import com.mongodb.baas.android.push.gcm.GCMPushProviderInfo;

/**
 * AvailablePushProviders is a collection of available push providers for an app and the information
 * needed to utilize them.
 */
public class AvailablePushProviders {
    private final GCMPushProviderInfo _gcm;

    public AvailablePushProviders() {
        this(null);
    }

    public AvailablePushProviders(
            final GCMPushProviderInfo gcm
    ) {
        _gcm = gcm;
    }

    /**
     * @return Whether or not the app supports push via Google Cloud Messaging.
     */
    public boolean hasGCM() {
        return _gcm != null;
    }

    /**
     * @return The Google Cloud Messaging information.
     */
    public GCMPushProviderInfo getGCM() {
        return _gcm;
    }

    public static class Builder {
        private GCMPushProviderInfo _gcm;

        public AvailablePushProviders build() {
            return new AvailablePushProviders(_gcm);
        }

        public void withGCM(final GCMPushProviderInfo gcmInfo) {
            _gcm = gcmInfo;
        }
    }
}
