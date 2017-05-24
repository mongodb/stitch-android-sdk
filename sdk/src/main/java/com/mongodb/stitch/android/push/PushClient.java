package com.mongodb.stitch.android.push;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.StitchClient;

import org.bson.Document;

/**
 * A PushClient is responsible for allowing users to register and deregister for push notifications
 * sent from Stitch or directly from the provider.
 */
public abstract class PushClient {

    static final String TAG = "Stitch-Push";

    // Preferences
    public static final String SHARED_PREFERENCES_NAME = "com.mongodb.stitch.sdk.push.SharedPreferences.%s";
    static final String PREF_CONFIGS = "gcm.configs";

    private final StitchClient _stitchClient;
    private final Context _context;
    private final SharedPreferences _globalPreferences;

    /**
     * @param context    The Android {@link Context} that this client should be bound to.
     * @param stitchClient The client to use for talking to Stitch.
     */
    public PushClient(
            final Context context,
            final StitchClient stitchClient
    ) {
        _stitchClient = stitchClient;
        _context = context;

        final String globPrefPath = String.format(SHARED_PREFERENCES_NAME, getStitchClient().getAppId());
        _globalPreferences = context.getSharedPreferences(globPrefPath, Context.MODE_PRIVATE);
    }

    /**
     * Registers the client with the provider and Stitch.
     *
     * @return A task that can resolved upon registering.
     */
    public abstract Task<Void> register();

    /**
     * Deregisters the client from the provider and Stitch.
     *
     * @return A task that can resolved upon deregistering.
     */
    public abstract Task<Void> deregister();

    /**
     * @return The Android {@link Context} associated with this client.
     */
    protected Context getContext() {
        return _context;
    }

    /**
     * @return The {@link StitchClient} this client is using.
     */
    protected StitchClient getStitchClient() {
        return _stitchClient;
    }

    /**
     * @param info The push provider info to persist.
     */
    protected synchronized void addInfoToConfigs(final PushProviderInfo info) {
        final Document configs = Document.parse(_globalPreferences.getString(PREF_CONFIGS, "{}"));
        configs.put(info.getService(), info.toDocument().toJson());
        _globalPreferences.edit().putString(PREF_CONFIGS, configs.toJson()).apply();
    }

    /**
     * @param info The push provider info to no longer persist.
     */
    protected synchronized void removeInfoFromConfigs(final PushProviderInfo info) {
        final Document configs = Document.parse(_globalPreferences.getString(PREF_CONFIGS, "{}"));
        configs.remove(info.getService());
        _globalPreferences.edit().putString(PREF_CONFIGS, configs.toJson()).apply();
    }

    /**
     * @param serviceName The service that will handle push for this client.
     * @return A generic device registration request.
     */
    protected Document getBaseRegisterPushRequest(final String serviceName) {
        final Document request = new Document();

        request.put(DeviceFields.SERVICE_NAME, serviceName);
        request.put(DeviceFields.DATA, new Document());

        return request;
    }

    /**
     * @param serviceName The service that handles push for this client.
     * @return A generic device deregistration request.
     */
    protected Document getBaseDeregisterPushDeviceRequest(final String serviceName) {
        final Document request = new Document();

        request.put(DeviceFields.SERVICE_NAME, serviceName);

        return request;
    }

    protected static class DeviceFields {
        static final String SERVICE_NAME = "service";
        public static final String DATA = "data";
    }

    protected static class Actions {
        public static final String REGISTER_PUSH = "registerPush";
        public static final String DEREGISTER_PUSH = "deregisterPush";
    }
}


