package com.mongodb.baas.android.push.gcm;

import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.gcm.GcmListenerService;
import com.mongodb.baas.android.push.PushMessage;

/**
 * GCMListenerService provides a way to receive {@link PushMessage}s from BaaS as well
 * as push notifications directly from GCM.
 */
public abstract class GCMListenerService extends GcmListenerService {

    private static final String TAG = "BaaS-GCM-Listener";

    /**
     * Called when a message is received from GCM. May not necessarily be from BaaS.
     *
     * @param data The data for the message.
     */
    @Override
    public void onMessageReceived(final String ignored, final Bundle data) {
        onPushMessageReceived(PushMessage.fromGCM(data));
    }

    /**
     * Called when a message is received from GCM. May not necessarily be from BaaS.
     *
     * @param message The parsed message possibly containing information from BaaS.
     */
    public void onPushMessageReceived(final PushMessage message) {
        if (!message.hasData()) {
            return;
        }
        Log.d(TAG, String.format("Received message: %s", message.getData().toString()));
    }
}
