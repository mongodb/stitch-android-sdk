package com.mongodb.baas.android.push.gcm;

import android.content.Context;
import android.content.SharedPreferences;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongodb.baas.android.push.PushProviderInfo;
import com.mongodb.baas.android.push.PushProviderName;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.mongodb.baas.android.push.PushClient.SHARED_PREFERENCES_NAME;
import static com.mongodb.baas.android.push.PushProviderName.GCM;
import static com.mongodb.baas.android.push.gcm.GCMPushClient.PREF_SENDER_IDS;

/**
 * GCMPushProviderInfo contains information needed to create a {@link GCMPushClient}.
 */
public class GCMPushProviderInfo implements PushProviderInfo {
    private final String _senderId;

    @JsonCreator
    private GCMPushProviderInfo(

            @JsonProperty("senderId")
            final String senderId
    ) {
        _senderId = senderId;
    }

    /**
     * @return A GCMPushProviderInfo indicating that its settings
     * should be read from provided properties.
     */
    public static GCMPushProviderInfo fromProperties() {
        return new GCMPushProviderInfo(null);
    }

    /**
     * @param senderId The GCM Sender ID.
     * @return A GCMPushProviderInfo sourced from a Sender ID.
     */
    public static GCMPushProviderInfo fromSenderId(final String senderId) {
        return new GCMPushProviderInfo(senderId);
    }

    /**
     * @param context     The Android {@link Context} that will allow preferences to be fetched.
     * @param clientAppId The app ID to scope preferences to.
     * @return The list of GCM information required to construct all active GCM clients.
     */
    public synchronized static List<PushProviderInfo> fromPreferences(final Context context, final String clientAppId) {
        final String globPrefPath = String.format(SHARED_PREFERENCES_NAME, clientAppId, GCM.getServiceName());
        final SharedPreferences preferences = context.getSharedPreferences(globPrefPath, Context.MODE_PRIVATE);
        final Set<String> senderIds = preferences.getStringSet(PREF_SENDER_IDS, new HashSet<String>());

        final List<PushProviderInfo> info = new ArrayList<>();
        for (final String senderId : senderIds) {
            info.add(GCMPushProviderInfo.fromSenderId(senderId));
        }
        return info;
    }

    /**
     * @return Whether or not this provider has a sender id associated with it.
     */
    public boolean hasSenderId() {
        return _senderId != null;
    }

    /**
     * @return The GCM Sender ID.
     */
    public String getSenderId() {
        return _senderId;
    }

    /**
     * @return The name of this provider.
     */
    @Override
    public PushProviderName getName() {
        return GCM;
    }
}
