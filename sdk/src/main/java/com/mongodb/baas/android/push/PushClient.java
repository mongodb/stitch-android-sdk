package com.mongodb.baas.android.push;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.mongodb.baas.android.BaasClient;
import com.mongodb.baas.android.BaasException;

import org.bson.Document;

/**
 * A PushClient is responsible for allowing users to register and deregister for push notifications
 * sent from BaaS or directly from the provider.
 */
public abstract class PushClient {

    // Preferences
    public static final String SHARED_PREFERENCES_NAME = "com.mongodb.baas.sdk.push.SharedPreferences.%s.%s";
    private static final String TAG = "BaaS-Push";
    private final BaasClient _baasClient;
    private final Context _context;

    /**
     * @param context    The Android {@link Context} that this client should be bound to.
     * @param baasClient The client to use for talking to BaaS.
     */
    public PushClient(
            final Context context,
            final BaasClient baasClient
    ) {
        _baasClient = baasClient;
        _context = context;
    }

    /**
     * Registers the client with the provider and BaaS.
     *
     * @return A task that can resolved upon registering.
     */
    public abstract Task<Void> register();

    /**
     * Deregisters the client from the provider and BaaS.
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
     * @return The {@link BaasClient} this client is using.
     */
    protected BaasClient getBaasClient() {
        return _baasClient;
    }

    /**
     * @param deviceId The device ID representing this client.
     * @return A generic device registration request.
     */
    protected Document getBaseRegisterPushDeviceRequest(final String deviceId) {
        final Document request = new Document();

        final String packageName = getContext().getPackageName();
        final PackageManager manager = getContext().getPackageManager();
        final PackageInfo info;
        try {
            info = manager.getPackageInfo(packageName, 0);
        } catch (final PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Error while getting info for app package", e);
            throw new BaasException.BaasClientException(e);
        }

        request.put(DeviceFields.USER_ID, _baasClient.getAuth().getUser().getId());
        request.put(DeviceFields.DEVICE_ID, deviceId);
        request.put(DeviceFields.APP_ID, getContext().getPackageName());
        request.put(DeviceFields.APP_VERSION, info.versionName);
        request.put(DeviceFields.PLATFORM, "android");
        request.put(DeviceFields.PLATFORM_VERSION, Build.VERSION.RELEASE);

        return request;
    }

    /**
     * @param deviceId The device ID representing this client.
     * @return A generic device deregistration request.
     */
    protected Document getBaseDeregisterPushDeviceRequest(final String deviceId) {
        final Document request = new Document();
        request.put(DeviceFields.USER_ID, _baasClient.getAuth().getUser().getId());
        request.put(DeviceFields.DEVICE_ID, deviceId);

        return request;
    }

    private static class DeviceFields {
        static final String USER_ID = "userId";
        static final String DEVICE_ID = "deviceId";
        static final String APP_ID = "appId";
        static final String APP_VERSION = "appVersion";
        static final String PLATFORM = "platform";
        static final String PLATFORM_VERSION = "platformVersion";
    }
}


