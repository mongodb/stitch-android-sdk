package com.mongodb.stitch.android;

import android.content.Context;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

/**
 *  A StitchClientFactory can be used to asynchronously create instances of StitchClient.
 */
public class StitchClientFactory {
    /**
     * @param context     The Android {@link Context} that this client should be bound to.
     * @param clientAppId The App ID for the Stitch app.
     * @param baseUrl     The base URL of the Stitch Client API server.
     * @param apiPath     The path for the resources
     * @return A task containing a StitchClient.
     */
    public static Task<StitchClient> create(final Context context,
                                            final String clientAppId,
                                            final String baseUrl,
                                            final String apiPath) {
        return Tasks.forResult(new StitchClient(context, clientAppId, baseUrl, apiPath));
    }

    /**
     * @param context     The Android {@link Context} that this client should be bound to.
     * @param clientAppId The App ID for the Stitch app.
     * @param baseUrl     The base URL of the Stitch Client API server.
     * @return A task containing a StitchClient.
     */
    public static Task<StitchClient> create(final Context context, final String clientAppId, String baseUrl) {
        return Tasks.forResult(new StitchClient(context, clientAppId, baseUrl, null));
    }

    /**
     * @param context     The Android {@link Context} that this client should be bound to.
     * @param clientAppId The App ID for the Stitch app.
     * @return A task containing a StitchClient.
     */
    public static Task<StitchClient> create(final Context context, final String clientAppId) {
        return Tasks.forResult(new StitchClient(context, clientAppId));
    }

    /**
     * @param context The Android {@link Context} that this client should be bound to.
     * @return A task containing a StitchClient derived from the properties file.
     */
    public static Task<StitchClient> createFromProperties(final Context context) {
        return Tasks.forResult(new StitchClient(context, null, null, null));
    }
}
