package com.mongodb.baas.android.push;

import com.mongodb.baas.android.push.gcm.GCMPushProviderInfo;

import org.bson.Document;

import java.util.Map;

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

    /**
     * @param json The data returned from BaaS about the providers.
     * @return A manifest of available push providers.
     */
    public static AvailablePushProviders fromQuery(final String json) {
        final Document doc = Document.parse(json);

        final AvailablePushProviders.Builder builder = new AvailablePushProviders.Builder();

        // Build push info
        for (final Map.Entry<String, Object> configEntry : doc.entrySet()) {
            final Document info = (Document) configEntry.getValue();

            final PushProviderName providerName =
                    PushProviderName.fromTypeName(info.getString(PushProviderInfo.Fields.TYPE));
            final Document config = (Document) info.get(PushProviderInfo.Fields.CONFIG);

            switch (providerName) {
                case GCM:
                    final GCMPushProviderInfo provider =
                            GCMPushProviderInfo.fromConfig(configEntry.getKey(), config);
                    builder.withGCM(provider);
                    break;
            }
        }

        return builder.build();
    }

    /**
     * Builder is a simple helper to build out a {@link AvailablePushProviders}
     */
    private static class Builder {
        private GCMPushProviderInfo _gcm;

        private AvailablePushProviders build() {
            return new AvailablePushProviders(_gcm);
        }

        private void withGCM(final GCMPushProviderInfo gcmInfo) {
            _gcm = gcmInfo;
        }
    }
}
