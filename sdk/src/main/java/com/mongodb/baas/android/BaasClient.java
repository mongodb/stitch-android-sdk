package com.mongodb.baas.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.mongodb.baas.android.auth.Auth;
import com.mongodb.baas.android.auth.AuthProvider;
import com.mongodb.baas.android.auth.AvailableAuthProviders;
import com.mongodb.baas.android.auth.RefreshTokenHolder;
import com.mongodb.baas.android.auth.anonymous.AnonymousAuthProviderInfo;
import com.mongodb.baas.android.auth.oauth2.facebook.FacebookAuthProviderInfo;
import com.mongodb.baas.android.auth.oauth2.google.GoogleAuthProviderInfo;
import com.mongodb.baas.android.push.AvailablePushProviders;
import com.mongodb.baas.android.push.PushClient;
import com.mongodb.baas.android.push.PushManager;
import com.mongodb.baas.android.push.PushProviderName;
import com.mongodb.baas.android.push.gcm.GCMPushProviderInfo;

import org.bson.Document;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import static com.mongodb.baas.android.BaasError.ErrorCode;
import static com.mongodb.baas.android.BaasError.parseRequestError;
import static com.mongodb.baas.android.BaasException.BaasAuthException;
import static com.mongodb.baas.android.BaasException.BaasAuthException.MUST_AUTH_MESSAGE;
import static com.mongodb.baas.android.BaasException.BaasRequestException;
import static com.mongodb.baas.android.BaasException.BaasServiceException;
import static com.mongodb.baas.android.Volley.AuthenticatedJsonStringRequest;
import static com.mongodb.baas.android.Volley.JsonStringRequest;

/**
 * A BaasClient is responsible for handling the overall interaction with all BaaS services.
 */
public class BaasClient {

    private static final String TAG = "BaaS";
    private static final String DEFAULT_BASE_URL = "https://baas-dev.10gen.cc";

    // Properties
    private static final String BAAS_PROPERTIES_FILE_NAME = "baas.properties";
    private static final String PROP_APP_ID = "appId";
    private static final String PROP_BASE_URL = "baseUrl";
    // Preferences
    private static final String SHARED_PREFERENCES_NAME = "com.mongodb.baas.sdk.SharedPreferences.%s";
    private static final String PREF_AUTH_JWT_NAME = "auth_token";
    private static final String PREF_AUTH_REFRESH_TOKEN_NAME = "refresh_token";
    private final Properties _properties;
    // Members
    private final String _baseUrl;
    private final String _clientAppId;
    private final RequestQueue _queue;
    private final ObjectMapper _objMapper;
    private final SharedPreferences _preferences;
    private final PushManager _pushManager;
    private final List<AuthListener> _authListeners;
    private Auth _auth;

    /**
     * @param context     The Android {@link Context} that this client should be bound to.
     * @param clientAppId The App ID for the BaaS app.
     * @param baseUrl     The base URL of the BaaS Client API server.
     */
    public BaasClient(final Context context, final String clientAppId, final String baseUrl) {
        _queue = Volley.newRequestQueue(context);
        _objMapper = CustomObjectMapper.createObjectMapper();

        final String prefPath = String.format(SHARED_PREFERENCES_NAME, clientAppId);
        _preferences = context.getSharedPreferences(prefPath, Context.MODE_PRIVATE);
        _authListeners = new ArrayList<>();
        _pushManager = new PushManager(context, this);

        // Only attempt to load properties
        _properties = new Properties();
        try {
            final InputStream propInput = context.getAssets().open(BAAS_PROPERTIES_FILE_NAME);
            _properties.load(propInput);
        } catch (final IOException ignored) {
        }

        if (clientAppId != null) {
            _clientAppId = clientAppId;
        } else {
            if (!_properties.containsKey(PROP_APP_ID)) {
                throw new BaasException.BaasClientException("No App ID in properties");
            }
            _clientAppId = _properties.getProperty(PROP_APP_ID);
        }

        if (baseUrl != null) {
            _baseUrl = baseUrl;
        } else if (!_properties.containsKey(PROP_BASE_URL)) {
            _baseUrl = DEFAULT_BASE_URL;
        } else {
            _baseUrl = _properties.getProperty(PROP_BASE_URL);
        }
    }

    /**
     * @param context     The Android {@link Context} that this client should be bound to.
     * @param clientAppId The App ID for the BaaS app.
     */
    public BaasClient(final Context context, final String clientAppId) {
        this(context, clientAppId, DEFAULT_BASE_URL);
    }

    public static BaasClient fromProperties(final Context context) {
        return new BaasClient(context, null, null);
    }

    // Public Methods

    public String getAppId() {
        return _clientAppId;
    }

    // Auth Methods

    /**
     * Gets the currently authenticated user. Must only be used when the client has been
     * previously authenticated.
     *
     * @return The currently Authenticated user.
     */
    public Auth getAuth() {
        if (!isAuthenticated()) {
            throw new BaasAuthException(MUST_AUTH_MESSAGE);
        }
        return _auth;
    }

    /**
     * @return Whether or not the client is authenticated.
     */
    public boolean isAuthenticated() {
        if (_auth != null) {
            return true;
        }

        if (_preferences.contains(PREF_AUTH_JWT_NAME)) {
            try {
                _auth = _objMapper.readValue(_preferences.getString(PREF_AUTH_JWT_NAME, ""), Auth.class);
            } catch (final IOException e) {
                throw new BaasException(e);
            }
            onLogin();
            return true;
        }

        return false;
    }

    /**
     * Logs out the current user.
     *
     * @return A task that can be resolved upon completion of logout.
     */
    public Task<Void> logout() {
        if (!isAuthenticated()) {
            return Tasks.forResult(null);
        }
        return executeRequest(Request.Method.DELETE, "auth", null, false, true).continueWith(new Continuation<String, Void>() {
            @Override
            public Void then(@NonNull final Task<String> task) throws Exception {
                if (task.isSuccessful()) {
                    clearAuth();
                    return null;
                }
                throw task.getException();
            }
        });
    }

    /**
     * Logs the current user in using a specific auth provider.
     *
     * @param authProvider The provider that will handle the login.
     * @return A task containing an {@link Auth} session that can be resolved on completion of log in.
     */
    public Task<Auth> logInWithProvider(AuthProvider authProvider) {

        if (isAuthenticated()) {
            Log.d(TAG, "Already logged in. Returning cached token");
            return Tasks.forResult(_auth);
        }

        final TaskCompletionSource<Auth> future = new TaskCompletionSource<>();
        final String url = String.format(
                "%s/v1/app/%s/auth/%s/%s",
                _baseUrl,
                _clientAppId,
                authProvider.getType(),
                authProvider.getName());

        final JsonStringRequest request = new JsonStringRequest(
                Request.Method.POST,
                url,
                authProvider.getAuthPayload().toString(),
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(final String response) {
                        try {
                            _auth = _objMapper.readValue(response, Auth.class);
                            final RefreshTokenHolder refreshToken =
                                    _objMapper.readValue(response, RefreshTokenHolder.class);
                            _preferences.edit().putString(PREF_AUTH_JWT_NAME, response).apply();
                            _preferences.edit().putString(PREF_AUTH_REFRESH_TOKEN_NAME, refreshToken.getToken()).apply();
                            future.setResult(_auth);
                        } catch (final IOException e) {
                            Log.e(TAG, "Error parsing auth response", e);
                            future.setException(new BaasException(e));
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(final VolleyError error) {
                        Log.e(TAG, "Error while logging in with auth provider", error);
                        future.setException(parseRequestError(error));
                    }
                });
        request.setTag(this);
        _queue.add(request);

        return future.getTask();
    }

    /**
     * Adds a listener for auth events.
     *
     * @param authListener The listener that will receive auth events.
     */
    public synchronized void addAuthListener(final AuthListener authListener) {
        _authListeners.add(authListener);
    }

    /**
     * Removes a listener for auth events.
     *
     * @param authListener The listener that will no longer receive auth events.
     */
    public synchronized void removeAuthListener(final AuthListener authListener) {
        _authListeners.remove(authListener);
    }

    /**
     * Gets all available auth providers for the current app.
     *
     * @return A task containing {@link AvailableAuthProviders} that can be resolved on completion
     * of the request.
     */
    public Task<AvailableAuthProviders> getAuthProviders() {

        final TaskCompletionSource<AvailableAuthProviders> future = new TaskCompletionSource<>();
        final String url = String.format("%s/v1/app/%s/auth", _baseUrl, _clientAppId);

        final JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(final JSONObject response) {

                        final AvailableAuthProviders.Builder builder = new AvailableAuthProviders.Builder();
                        // Build provider info
                        for (final Iterator<String> keyItr = response.keys(); keyItr.hasNext(); ) {
                            final String authProviderName = keyItr.next();

                            try {
                                final JSONObject info = response.getJSONObject(authProviderName);

                                switch (authProviderName) {
                                    case FacebookAuthProviderInfo.FQ_NAME:
                                        final FacebookAuthProviderInfo fbInfo =
                                                _objMapper.readValue(info.toString(), FacebookAuthProviderInfo.class);
                                        builder.withFacebook(fbInfo);
                                        break;
                                    case GoogleAuthProviderInfo.FQ_NAME:
                                        final GoogleAuthProviderInfo googleInfo =
                                                _objMapper.readValue(info.toString(), GoogleAuthProviderInfo.class);
                                        builder.withGoogle(googleInfo);
                                        break;
                                    case AnonymousAuthProviderInfo.FQ_NAME:
                                        final AnonymousAuthProviderInfo anonInfo =
                                                _objMapper.readValue(info.toString(), AnonymousAuthProviderInfo.class);
                                        builder.withAnonymous(anonInfo);
                                        break;
                                }
                            } catch (final JSONException | IOException e) {
                                Log.e(
                                        TAG,
                                        String.format("Error while getting auth provider info for '%s'", authProviderName),
                                        e);
                                future.setException(e);
                                return;
                            }
                        }
                        future.setResult(builder.build());
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(final VolleyError error) {
                        Log.e(TAG, "Error while getting auth provider info", error);
                        future.setException(parseRequestError(error));
                    }
                });
        request.setTag(this);
        _queue.add(request);

        return future.getTask();
    }

    // Pipelines

    /**
     * Executes a pipeline with the current app.
     *
     * @param pipeline The pipeline to execute.
     * @return A task containing the result of the pipeline that can be resolved on completion
     * of the execution.
     */
    @SuppressWarnings("unchecked")
    public Task<List<Object>> executePipeline(final List<PipelineStage> pipeline) {
        ensureAuthenticated();
        final String pipeStr;
        try {
            pipeStr = _objMapper.writeValueAsString(pipeline);
        } catch (final IOException e) {
            return Tasks.forException(e);
        }

        return executeRequest(Request.Method.POST, "pipeline", pipeStr).continueWith(new Continuation<String, List<Object>>() {
            @Override
            public List<Object> then(@NonNull final Task<String> task) throws Exception {
                if (task.isSuccessful()) {
                    final Document doc = Document.parse(task.getResult());
                    return (List<Object>) doc.get("result");
                } else {
                    Log.e(TAG, "Error while executing pipeline", task.getException());
                    throw task.getException();
                }
            }
        });
    }

    /**
     * Executes a pipeline with the current app.
     *
     * @param stages The stages to execute as a contiguous pipeline.
     * @return A task containing the result of the pipeline that can be resolved on completion
     * of the execution.
     */
    public Task<List<Object>> executePipeline(final PipelineStage... stages) {
        return executePipeline(Arrays.asList(stages));
    }

    // Network

    /**
     * Executes a network request against the app. The request will be retried if there
     * is an access token expiration.
     *
     * @param method   The HTTP method to use.
     * @param resource The resource to target.
     * @return A task containing the body of the network response that can be resolved on completion
     * of the network request.
     */
    public Task<String> executeRequest(
            final int method,
            final String resource
    ) {
        return executeRequest(method, resource, null, true, false);
    }

    /**
     * Executes a network request against the app. The request will be retried if there
     * is an access token expiration.
     *
     * @param method   The HTTP method to use.
     * @param resource The resource to target.
     * @param body     The JSON body to include in the request.
     * @return A task containing the body of the network response that can be resolved on completion
     * of the network request.
     */
    private Task<String> executeRequest(
            final int method,
            final String resource,
            final String body
    ) {
        return executeRequest(method, resource, body, true, false);
    }

    /**
     * Executes a network request against the app.
     *
     * @param method           The HTTP method to use.
     * @param resource         The resource to target.
     * @param body             The JSON body to include in the request.
     * @param refreshOnFailure Whether or not to refresh the access token if it expires.
     * @param useRefreshToken  Whether or not to use the refresh token over the access token.
     * @return A task containing the body of the network response that can be resolved on completion
     * of the network request.
     */
    public Task<String> executeRequest(
            final int method,
            final String resource,
            final String body,
            final boolean refreshOnFailure,
            final boolean useRefreshToken
    ) {
        ensureAuthenticated();
        final String url = String.format("%s/v1/app/%s/%s", _baseUrl, _clientAppId, resource);
        final String token = useRefreshToken ? getRefreshToken() : _auth.getAccessToken();
        final TaskCompletionSource<String> future = new TaskCompletionSource<>();
        final AuthenticatedJsonStringRequest request = new AuthenticatedJsonStringRequest(
                method,
                url,
                body,
                Collections.singletonMap(
                        "Authorization",
                        String.format("Bearer %s", token)),
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(final String response) {
                        future.setResult(response);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(final VolleyError error) {
                        final BaasRequestException e = parseRequestError(error);
                        if (e instanceof BaasServiceException) {
                            if (((BaasServiceException) e).getErrorCode() == ErrorCode.INVALID_SESSION) {
                                if (!refreshOnFailure) {
                                    clearAuth();
                                    future.setException(e);
                                    return;
                                }
                                handleInvalidSession(method, resource, body, future);
                                return;
                            }
                        }
                        future.setException(e);
                    }
                });
        request.setTag(this);
        _queue.add(request);

        return future.getTask();
    }

    // Push

    /**
     * @return The manager for {@link PushClient}s.
     */
    public PushManager getPush() {
        return _pushManager;
    }

    /**
     * Gets all available push providers for the current app.
     *
     * @return A task containing {@link AvailablePushProviders} that can be resolved on completion
     * of the request.
     */
    public Task<AvailablePushProviders> getPushProviders() {

        return executeRequest(Request.Method.GET, "push").continueWith(new Continuation<String, AvailablePushProviders>() {
            @Override
            public AvailablePushProviders then(@NonNull final Task<String> task) throws Exception {
                final Document doc = Document.parse(task.getResult());

                final AvailablePushProviders.Builder builder = new AvailablePushProviders.Builder();

                // Build push info
                for (final String pushProviderSvcName : doc.keySet()) {
                    try {
                        final Document info = (Document) doc.get(pushProviderSvcName);

                        final PushProviderName providerName =
                                PushProviderName.fromServiceName(pushProviderSvcName);
                        switch (providerName) {
                            case GCM:
                                final GCMPushProviderInfo gcmInfo =
                                        _objMapper.readValue(info.toJson(), GCMPushProviderInfo.class);
                                builder.withGCM(gcmInfo);
                                break;
                        }
                    } catch (final ClassCastException | IOException e) {
                        Log.e(
                                TAG,
                                String.format("Error while getting push info for '%s'", pushProviderSvcName),
                                e);
                        throw e;
                    }
                }

                return builder.build();
            }
        });
    }

    // Internal Public Methods
    public Properties getProperties() {
        return _properties;
    }

    // Private Methods

    // Auth

    /**
     * Checks if the client is authenticated and if it isn't it throws.
     */
    private void ensureAuthenticated() {
        if (!isAuthenticated()) {
            throw new BaasAuthException(MUST_AUTH_MESSAGE);
        }
    }

    /**
     * Called when a user logs in with this client.
     */
    private synchronized void onLogin() {
        for (final AuthListener listener : _authListeners) {
            listener.onLogin();
        }
    }

    /**
     * Called when a user is logged out from this client.
     */
    private synchronized void onLogout(final String lastProvider) {
        for (final AuthListener listener : _authListeners) {
            listener.onLogout(lastProvider);
        }
    }

    /**
     * Gets the refresh token for the current user if authenticated; throws otherwise.
     */
    private String getRefreshToken() {
        if (!isAuthenticated()) {
            throw new BaasAuthException(MUST_AUTH_MESSAGE);
        }

        return _preferences.getString(PREF_AUTH_REFRESH_TOKEN_NAME, "");
    }

    /**
     * Clears all authentication material that has been persisted.
     */
    private void clearAuth() {
        if (_auth == null) {
            return;
        }
        final String lastProvider = _auth.getProvider();
        _auth = null;
        _preferences.edit().remove(PREF_AUTH_JWT_NAME).apply();
        _preferences.edit().remove(PREF_AUTH_REFRESH_TOKEN_NAME).apply();
        _queue.cancelAll(this);
        onLogout(lastProvider);
    }

    /**
     * Handles an invalid session error from BaaS by refreshing the access token and
     * retrying the original request.
     *
     * @param method   The original HTTP method.
     * @param resource The original resource.
     * @param body     The original body.
     * @param future   The task to resolve upon completion of this handler.
     */
    private void handleInvalidSession(
            final int method,
            final String resource,
            final String body,
            final TaskCompletionSource<String> future
    ) {
        refreshAccessToken().addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull final Task<Void> task) {
                if (!task.isSuccessful()) {
                    future.setException(task.getException());
                    return;
                }

                // Retry one more time
                executeRequest(method, resource, body, false, false).addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull final Task<String> task) {
                        if (task.isSuccessful()) {
                            future.setResult(task.getResult());
                            return;
                        }

                        future.setException(task.getException());
                    }
                });
            }
        });
    }

    /**
     * Refreshes the current access token using the current refresh token.
     *
     * @return A task that can resolved upon completion of refreshing the access token.
     */
    private Task<Void> refreshAccessToken() {
        return executeRequest(Request.Method.POST, "auth/newAccessToken", null, false, true)
                .continueWith(new Continuation<String, Void>() {
                    @Override
                    public Void then(@NonNull Task<String> task) throws Exception {
                        if (!task.isSuccessful()) {
                            throw task.getException();
                        }

                        final String newAccessToken;
                        try {
                            final JSONObject response = new JSONObject(task.getResult());
                            newAccessToken = response.getString("accessToken");
                        } catch (final JSONException e) {
                            Log.e(TAG, "Error parsing access token response", e);
                            throw new BaasException(e);
                        }

                        _auth = _auth.withNewAccessToken(newAccessToken);

                        final String authJson;
                        try {
                            authJson = _objMapper.writeValueAsString(_auth);
                        } catch (final IOException e) {
                            Log.e(TAG, "Error parsing auth response", e);
                            throw new BaasException(e);
                        }

                        _preferences.edit().putString(PREF_AUTH_JWT_NAME, authJson).apply();
                        return null;
                    }
                });
    }
}
