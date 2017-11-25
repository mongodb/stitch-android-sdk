package com.mongodb.stitch.android.push;

import android.content.Context;

import com.mongodb.stitch.android.AuthListener;
import com.mongodb.stitch.android.StitchClient;
import com.mongodb.stitch.android.StitchException;
import com.mongodb.stitch.android.push.gcm.GCMPushClient;
import com.mongodb.stitch.android.push.gcm.GCMPushProviderInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * PushManager is responsible for handling the creation of {@link PushClient}s while handling
 * any events from the {@link StitchClient} that require changes to the clients.
 */
public class PushManager implements AuthListener {

    private final Context _context;
    private final StitchClient _stitchClient;
    private final Map<PushClient, PushClient> _clients;

    /**
     * @param context    The Android {@link Context} that this client should be bound to.
     * @param stitchClient The client to use for talking to Stitch.
     */
    public PushManager(final Context context, final StitchClient stitchClient) {
        _context = context;
        _stitchClient = stitchClient;
        _clients = new HashMap<>();
        _stitchClient.addAuthListener(this);
    }

    /**
     * @param info Information required to build a client.
     * @return A {@link PushClient} representing the given provider.
     */
    public synchronized PushClient forProvider(final PushProviderInfo info) {
        final PushClient client;
        switch (info.getProvider()) {
            case GCM:
                client = new GCMPushClient(_context, _stitchClient, (GCMPushProviderInfo) info);
                break;
            default:
                throw new StitchException.StitchClientException("Unknown push provider");
        }

        if (_clients.containsKey(client)) {
            return _clients.get(client);
        }

        _clients.put(client, client);
        return client;
    }

    /**
     * Does nothing upon login.
     */
    @Override
    public void onLogin() {
    }

    /**
     * Deregisters all active and previously active clients. This is only a best effort and
     * there may be a period of time where the application will still receive notifications.
     */
    @Override
    public synchronized void onLogout() {

        // Create any missing clients from saved data
        for (final PushProviderInfo info : PushProviderInfo.fromPreferences(_context, _stitchClient.getAppId())) {
            this.forProvider(info);
        }

        // Notify Stitch that we no longer want updates
        for (final PushClient client : _clients.values()) {
            client.deregister();
        }
        _clients.clear();
    }
}
