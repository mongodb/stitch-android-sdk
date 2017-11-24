package com.mongodb.stitch.android.push;

import com.mongodb.stitch.android.push.gcm.GCMPushProviderInfo;

import org.bson.Document;

import static com.mongodb.stitch.android.BsonUtils.parseIterable;

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
     * @param json The data returned from Stitch about the providers.
     * @return A manifest of available push providers.
     */
    public static AvailablePushProviders fromQuery(final String json) {
        final Iterable providers = parseIterable(json);
        final AvailablePushProviders.Builder builder = new AvailablePushProviders.Builder();

        // Build push info
        for (final Object providerObj : providers) {
            final Document info = (Document) providerObj;

            final String serviceName = info.getString(PushProviderInfo.Fields.NAME);
            final PushProviderName providerName =
                    PushProviderName.fromTypeName(info.getString(PushProviderInfo.Fields.TYPE));
            final Document config = (Document) info.get(PushProviderInfo.Fields.CONFIG);

            switch (providerName) {
                case GCM:
                    final GCMPushProviderInfo provider =
                            GCMPushProviderInfo.fromConfig(serviceName, config);
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
