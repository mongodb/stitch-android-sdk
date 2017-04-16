package com.mongodb.baas.android.push.gcm;

import com.mongodb.baas.android.push.PushProviderInfo;

import org.bson.Document;

import static com.mongodb.baas.android.push.PushProviderName.GCM;

/**
 * GCMPushProviderInfo contains information needed to create a {@link GCMPushClient}.
 */
public class GCMPushProviderInfo extends PushProviderInfo {

    private final String _senderId;
    private final boolean _fromProperties;

    private GCMPushProviderInfo(
            final String serviceName,
            final String senderId,
            final boolean fromProperties
    ) {
        super(GCM, serviceName);
        _senderId = senderId;
        _fromProperties = fromProperties;
    }

    /**
     * @return A GCMPushProviderInfo indicating that its settings
     * should be read from provided properties.
     */
    public static GCMPushProviderInfo fromProperties() {
        return new GCMPushProviderInfo(null, null, true);
    }

    /**
     * @param serviceName The service that will handle push for this provider.
     * @param config The persisted configuration of this provider.
     * @return A GCMPushProviderInfo sourced from a persisted config.
     */
    public static GCMPushProviderInfo fromConfig(final String serviceName, final Document config) {
        final String senderId = config.getString(Fields.SENDER_ID);
        return new GCMPushProviderInfo(serviceName, senderId, false);
    }

    /**
     * @param serviceName The service that will handle push for this provider.
     * @param senderId The GCM Sender ID.
     * @return A GCMPushProviderInfo sourced from a Sender ID.
     */
    public static GCMPushProviderInfo fromSenderId(final String serviceName, final String senderId) {
        return new GCMPushProviderInfo(serviceName, senderId, false);
    }

    /**
     * @return Whether or not the info should be sourced from properties.
     */
    boolean isFromProperties() {
        return _fromProperties;
    }

    /**
     * @return The GCM Sender ID.
     */
    public String getSenderId() {
        return _senderId;
    }

    /**
     * @return The provider info as a serializable document.
     */
    @Override
    public Document toDocument() {
        final Document doc = super.toDocument();
        final Document config = (Document) doc.get(PushProviderInfo.Fields.CONFIG);
        config.put(Fields.SENDER_ID, _senderId);
        return doc;
    }

    private static class Fields {
        private static final String SENDER_ID = "senderId";
    }
}
