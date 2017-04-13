package com.mongodb.baas.android.push.gcm;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.gcm.GcmPubSub;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.mongodb.baas.android.BaasClient;
import com.mongodb.baas.android.BaasException;
import com.mongodb.baas.android.PipelineStage;
import com.mongodb.baas.android.push.PushClient;

import org.bson.Document;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.mongodb.baas.android.push.PushProviderName.GCM;

/**
 * GCMPushClient is the {@link PushClient} for GCM. It handles the logic of registering and
 * deregistering with both GCM and BaaS.
 * <p>
 * It does not actively handle updates to the Instance ID when it is refreshed.
 */
public class GCMPushClient extends PushClient {

    // Preferences
    static final String PREF_SENDER_IDS = "gcm.senderIds";
    private static final String TAG = "BaaS-Push-GCM";
    // Properties
    private static final String PROP_GCM_SENDER_ID = "push.gcm.senderId";
    private final SharedPreferences _globalPreferences;
    private final String _senderId;

    /**
     * @param context    The Android {@link Context} that this client should be bound to.
     * @param baasClient The client to use for talking to BaaS.
     * @param info       The information required to construct this client.
     */
    public GCMPushClient(
            final Context context,
            final BaasClient baasClient,
            final GCMPushProviderInfo info
    ) {
        super(context, baasClient);

        final GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        if (apiAvailability.isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
            throw new BaasException.BaasClientException(
                    "Google Play Services is not currently available and is necessary for GCM push notifications");
        }

        if (!info.hasSenderId()) {
            if (!baasClient.getProperties().containsKey(PROP_GCM_SENDER_ID)) {
                throw new BaasException.BaasClientException("No GCM Sender ID in properties");
            }
            _senderId = baasClient.getProperties().getProperty(PROP_GCM_SENDER_ID);
        } else {
            _senderId = info.getSenderId();
        }

        final String globPrefPath = String.format(SHARED_PREFERENCES_NAME, getBaasClient().getAppId(), GCM.getServiceName());
        _globalPreferences = context.getSharedPreferences(globPrefPath, Context.MODE_PRIVATE);
    }

    /**
     * @return A task that can be resolved upon completion of registration to both GCM and BaaS.
     */
    @Override
    public Task<Void> register() {
        final InstanceID instanceId = InstanceID.getInstance(getContext());

        return getRegistrationToken(instanceId, _senderId)
                .continueWithTask(new Continuation<String, Task<Void>>() {
                    @Override
                    public Task<Void> then(@NonNull final Task<String> task) throws Exception {
                        if (!task.isSuccessful()) {
                            throw task.getException();
                        }

                        return registerWithServer(instanceId, task.getResult());
                    }
                });
    }

    /**
     * @return A task that can be resolved upon completion of deregistration from both GCM and BaaS.
     */
    @Override
    public Task<Void> deregister() {
        final InstanceID instanceId = InstanceID.getInstance(getContext());

        return deleteRegistrationToken(instanceId, _senderId)
                .continueWithTask(new Continuation<Void, Task<Void>>() {
                    @Override
                    public Task<Void> then(@NonNull final Task<Void> task) throws Exception {
                        if (!task.isSuccessful()) {
                            throw task.getException();
                        }

                        return deregisterWithServer(instanceId);
                    }
                });
    }

    /**
     * Subscribes the client to a specific topic.
     *
     * @return A task that can resolved upon subscribing.
     */
    public Task<Void> subscribeToTopic(final String topic) {
        final GcmPubSub pubSub = GcmPubSub.getInstance(getContext());
        final String topicKey = String.format("/topics/%s", topic);
        final InstanceID instanceId = InstanceID.getInstance(getContext());

        final TaskCompletionSource<Void> future = new TaskCompletionSource<>();

        getRegistrationToken(instanceId, _senderId).addOnCompleteListener(new OnCompleteListener<String>() {
            @Override
            public void onComplete(@NonNull final Task<String> task) {
                if (!task.isSuccessful()) {
                    future.setException(task.getException());
                }

                new AsyncTask<Object, Integer, String>() {
                    @Override
                    protected String doInBackground(final Object[] ignored) {

                        try {
                            pubSub.subscribe(task.getResult(), topicKey, null);
                        } catch (final IOException e) {
                            Log.e(TAG, "Error subscribing to " + topicKey, e);
                            future.setException(e);
                            return null;
                        }

                        future.setResult(null);
                        return null;
                    }
                }.execute();
            }
        });

        return future.getTask();
    }

    /**
     * Subscribes the client to a specific topic.
     *
     * @return A task that can resolved upon subscribing.
     */
    public Task<Void> unsubscribeFromTopic(final String topic) {
        final GcmPubSub pubSub = GcmPubSub.getInstance(getContext());
        final String topicKey = String.format("/topics/%s", topic);
        final InstanceID instanceId = InstanceID.getInstance(getContext());

        final TaskCompletionSource<Void> future = new TaskCompletionSource<>();

        getRegistrationToken(instanceId, _senderId).addOnCompleteListener(new OnCompleteListener<String>() {
            @Override
            public void onComplete(@NonNull final Task<String> task) {
                if (!task.isSuccessful()) {
                    future.setException(task.getException());
                }

                new AsyncTask<Object, Integer, String>() {
                    @Override
                    protected String doInBackground(final Object[] ignored) {

                        try {
                            pubSub.unsubscribe(task.getResult(), topicKey);
                        } catch (final IOException e) {
                            Log.e(TAG, "Error unsubscribing from " + topicKey, e);
                            future.setException(e);
                            return null;
                        }

                        future.setResult(null);
                        return null;
                    }
                }.execute();
            }
        });

        return future.getTask();
    }

    /**
     * Deletes the current registration token.
     *
     * @param instanceId The instance ID which generated the registration token.
     * @param senderId   The sender ID to revoke the registration token from.
     * @return A task that can be resolved upon deletion of the token.
     */
    private Task<Void> deleteRegistrationToken(final InstanceID instanceId, final String senderId) {
        final TaskCompletionSource<Void> future = new TaskCompletionSource<>();
        new AsyncTask<Object, Integer, String>() {
            @Override
            protected String doInBackground(final Object[] ignored) {

                try {
                    instanceId.deleteToken(senderId, GoogleCloudMessaging.INSTANCE_ID_SCOPE);
                } catch (final IOException e) {
                    Log.e(TAG, "Error deleting GCM registration token", e);
                    future.setException(e);
                    return null;
                }

                future.setResult(null);
                return null;
            }
        }.execute();

        return future.getTask();
    }

    /**
     * Gets or creates a registration token.
     *
     * @param instanceId The instance ID which should generate the registration token.
     * @param senderId   The sender ID to generate the registration token for.
     * @return A task that can be resolved upon creating/retrieval of the token.
     */
    private Task<String> getRegistrationToken(final InstanceID instanceId, final String senderId) {
        final TaskCompletionSource<String> future = new TaskCompletionSource<>();
        new AsyncTask<Object, Integer, String>() {
            @Override
            protected String doInBackground(final Object[] ignored) {

                final String registrationToken;
                try {
                    registrationToken = instanceId.getToken(senderId, GoogleCloudMessaging.INSTANCE_ID_SCOPE);
                } catch (final IOException e) {
                    Log.e(TAG, "Error getting GCM registration token", e);
                    future.setException(e);
                    return null;
                }

                future.setResult(registrationToken);
                return null;
            }
        }.execute();

        return future.getTask();
    }

    /**
     * Register the registration token with BaaS.
     *
     * @param instanceId        The instance ID which generated the registration token.
     * @param registrationToken The registration token generated for the sender.
     * @return A task that can be resolved upon registering the token with BaaS.
     */
    private Task<Void> registerWithServer(final InstanceID instanceId, final String registrationToken) {
        final Map<String, Object> request = getRegisterPushDeviceRequest(instanceId, registrationToken);
        return getBaasClient().executePipeline(new PipelineStage(
                Actions.REGISTER_DEVICE, GCM.getServiceName(), request))
                .continueWith(new Continuation<List<Object>, Void>() {
                    @Override
                    public Void then(@NonNull Task<List<Object>> task) throws Exception {
                        if (!task.isSuccessful()) {
                            throw task.getException();
                        }

                        addSenderId();
                        return null;
                    }
                });
    }

    /**
     * Deregister the device associated with the registration token from BaaS.
     *
     * @param instanceId The instance ID which generated the registration token.
     * @return A task that can be resolved upon deregistering the device from BaaS.
     */
    private Task<Void> deregisterWithServer(final InstanceID instanceId) {
        final Map<String, Object> request = getDeregisterPushDeviceRequest(instanceId);
        return getBaasClient().executePipeline(new PipelineStage(
                Actions.DEREGISTER_DEVICE, GCM.getServiceName(), request))
                .continueWith(new Continuation<List<Object>, Void>() {
                    @Override
                    public Void then(@NonNull Task<List<Object>> task) throws Exception {
                        if (!task.isSuccessful()) {
                            throw task.getException();
                        }

                        removeSenderId();
                        return null;
                    }
                });
    }

    /**
     * Add current sender ID to saved set of active sender IDs.
     */
    private synchronized void addSenderId() {
        final Set<String> senderIds = _globalPreferences.getStringSet(PREF_SENDER_IDS, new HashSet<String>());
        senderIds.add(_senderId);
        _globalPreferences.edit().putStringSet(PREF_SENDER_IDS, senderIds).apply();
    }

    /**
     * Remove current sender ID from saved set of active sender IDs.
     */
    private synchronized void removeSenderId() {
        final Set<String> senderIds = _globalPreferences.getStringSet(PREF_SENDER_IDS, new HashSet<String>());
        senderIds.remove(_senderId);
        _globalPreferences.edit().putStringSet(PREF_SENDER_IDS, senderIds).apply();
    }

    /**
     * @param instanceId        The instance ID which generated the registration token.
     * @param registrationToken The registration token generated for the sender.
     * @return A GCM specific device registration request.
     */
    private Document getRegisterPushDeviceRequest(final InstanceID instanceId, final String registrationToken) {

        final Document request = getBaseRegisterPushDeviceRequest(instanceId.getId());
        request.put(DeviceFields.PUSH_ID, registrationToken);

        return request;
    }

    /**
     * @param instanceId The instance ID which generated the registration token.
     * @return A GCM specific device deregistration request.
     */
    private Document getDeregisterPushDeviceRequest(final InstanceID instanceId) {
        return getBaseDeregisterPushDeviceRequest(instanceId.getId());
    }

    /**
     * Equality for a {@link GCMPushClient} is defined by its sender ID.
     *
     * @param other The other client to compare against.
     * @return Whether or not these clients are the same.
     */
    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof GCMPushClient)) {
            return false;
        }

        return _senderId.equals(((GCMPushClient) other)._senderId);
    }

    /**
     * Note: Future clients should add a type to the hash to avoid collisions.
     *
     * @return A hash code for this client represented by its sender ID.
     */
    @Override
    public int hashCode() {
        return _senderId.hashCode();
    }

    private static class DeviceFields {
        public static final String PUSH_ID = "pushId";
    }

    private static class Actions {
        public static final String REGISTER_DEVICE = "registerDevice";
        public static final String DEREGISTER_DEVICE = "deregisterDevice";
    }
}
