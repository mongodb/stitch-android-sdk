package com.mongodb.baas.sdk;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.mongodb.baas.sdk.BaasException.BaasServiceException.ErrorCode;
import com.mongodb.baas.sdk.auth.Auth;
import com.mongodb.baas.sdk.auth.AuthProvider;
import com.mongodb.baas.sdk.auth.AuthProviderInfo;
import com.mongodb.baas.sdk.auth.RefreshTokenHolder;
import com.mongodb.baas.sdk.auth.anonymous.AnonymousAuthProvider;
import com.mongodb.baas.sdk.auth.anonymous.AnonymousAuthProviderInfo;
import com.mongodb.baas.sdk.auth.facebook.FacebookAuthProviderInfo;
import com.mongodb.baas.sdk.auth.google.GoogleAuthProviderInfo;

import org.bson.Document;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static com.mongodb.baas.sdk.BaasException.*;
import static com.mongodb.baas.sdk.Volley.*;

public class BaasClient {
    private static final String TAG = "BaaS";
    private static final String DEFAULT_BASE_URL = "https://baas-dev.10gen.cc";
    private static final String SHARED_PREFERENCES_NAME = "com.mongodb.baas.sdk.SharedPreferences";
    private static final String AUTH_JWT_NAME = "auth_token";
    private static final String AUTH_REFRESH_TOKEN_NAME = "refresh_token";

    private final String _baseUrl;
    private final String _appName;
    private final RequestQueue _queue;
    private final ObjectMapper _objMapper;

    private final SharedPreferences _preferences;
    private Auth _auth;

    private List<AuthListener> _authListeners;

    public BaasClient(final Context context, final String appName, final String baseUrl) {
        _appName = appName;
        _queue = Volley.newRequestQueue(context);
        _objMapper = CustomObjectMapper.createObjectMapper();
        _baseUrl = baseUrl;
        _preferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);

        _authListeners = new ArrayList<>();
    }

    public BaasClient(final Context context, final String appName) {
        this(context, appName, DEFAULT_BASE_URL);
    }

    public void addAuthListener(final AuthListener authListener) {
        _authListeners.add(authListener);
    }

    public void removeAuthListener(final AuthListener authListener) {
        _authListeners.remove(authListener);
    }

    private void onLogin() {
        for (final AuthListener listener : _authListeners) {
            listener.onLogin();
        }
    }

    private void onLogout(final String lastProvider) {
        for (final AuthListener listener : _authListeners) {
            listener.onLogout(lastProvider);
        }
    }

    public Auth getAuth() {
        if (!isAuthed()) {
            throw new BaasAuthException("No auth");
        }
        return _auth;
    }

    private String getRefreshToken() {
        if (!isAuthed()) {
            throw new BaasAuthException("No auth");
        }

        return _preferences.getString(AUTH_REFRESH_TOKEN_NAME, "");
    }

    public boolean isAuthed() {
        if (_auth != null) {
            return true;
        }

        if (_preferences.contains(AUTH_JWT_NAME)) {
            try {
                _auth = _objMapper.readValue(_preferences.getString(AUTH_JWT_NAME, ""), Auth.class);
            } catch (final IOException e) {
                throw new BaasException(e);
            }
            onLogin();
            return true;
        }

        return false;
    }

    private void ensureAuthed() {
        if (!isAuthed()) {
            throw new BaasAuthException("Must first authenticate");
        }
    }

    public Task<AuthProviderInfo> getAuthProviders() {

        final TaskCompletionSource<AuthProviderInfo> future = new TaskCompletionSource<>();
        final String url = String.format("%s/v1/app/%s/auth", _baseUrl, _appName);

        final JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(final JSONObject response) {

                        final AuthProviderInfo.Builder builder = new AuthProviderInfo.Builder();
                        // Build provider info
                        for (final Iterator<String> keyItr = response.keys(); keyItr.hasNext();) {
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

    private BaasRequestException parseRequestError(final VolleyError error) {

        if (error.networkResponse == null) {
            return new BaasRequestException(error);
        }

        final String data;
        try {
            data = new String(
                    error.networkResponse.data,
                    HttpHeaderParser.parseCharset(error.networkResponse.headers, "utf-8"));
        } catch (final UnsupportedEncodingException e) {
            throw new BaasRequestException(e);
        }

        final String errorMsg;
        if (error.networkResponse.headers.containsKey("Content-Type") &&
                error.networkResponse.headers.get("Content-Type").equals("application/json")) {
            try {
                final JSONObject obj = new JSONObject(data);
                errorMsg = obj.getString("error");
                if (obj.has("errorCode")) {
                    final String errorCode = obj.getString("errorCode");
                    return parseErrorCode(errorMsg, errorCode);
                }
            } catch (final JSONException e) {
                throw new BaasRequestException(e);
            }
        } else {
            errorMsg = data;
        }

        if (error.networkResponse.statusCode >= 400 &&  error.networkResponse.statusCode < 600) {
            return new BaasServiceException(errorMsg);
        }

        return new BaasRequestException(error);
    }

    private BaasServiceException parseErrorCode(final String msg, final String code) {
        switch (code) {
            case "InvalidSession":
                return new BaasServiceException(msg, ErrorCode.INVALID_SESSION);
        }
        return new BaasServiceException(msg);
    }

    private void clearAuth() {
        if (_auth == null) {
            return;
        }
        final String lastProvider = _auth.getProvider();
        _auth = null;
        _preferences.edit().remove(AUTH_JWT_NAME).apply();
        _preferences.edit().remove(AUTH_REFRESH_TOKEN_NAME).apply();
        _queue.cancelAll(this);
        onLogout(lastProvider);
    }

    public Task<Void> logout() {
        if (!isAuthed()) {
            return Tasks.forResult(null);
        }
        return executeRequest(Request.Method.DELETE, "auth", null, false, true).continueWithTask(new Continuation<String, Task<Void>>() {
            @Override
            public Task<Void> then(@NonNull final Task<String> task) throws Exception {
                if (task.isSuccessful()) {
                    clearAuth();
                    return Tasks.forResult(null);
                }
                return Tasks.forException(task.getException());
            }
        });
    }

    public Task<Auth> logInWithProvider(AuthProvider authProvider) {

        if (isAuthed()) {
            Log.d(TAG, "Already logged in. Returning cached token");
            return Tasks.forResult(_auth);
        }

        final TaskCompletionSource<Auth> future = new TaskCompletionSource<>();
        final String url = String.format(
                "%s/v1/app/%s/auth/%s/%s",
                _baseUrl,
                _appName,
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
                            _preferences.edit().putString(AUTH_JWT_NAME, response).apply();
                            _preferences.edit().putString(AUTH_REFRESH_TOKEN_NAME, refreshToken.getToken()).apply();
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

    public Task<String> executeRequest(
            final int method,
            final String resource,
            final String body
    ) {
        return executeRequest(method, resource, body, true, false);
    }

    private Task<String> executeRequest(
            final int method,
            final String resource,
            final String body,
            final boolean refreshOnFailure,
            final boolean useRefreshToken
    ) {
        ensureAuthed();
        final String url = String.format("%s/v1/app/%s/%s", _baseUrl, _appName, resource);
        final String token = useRefreshToken ? getRefreshToken() : _auth.getAccessToken();
        final TaskCompletionSource<String> future = new TaskCompletionSource<>();
        final AuthedJsonStringRequest request = new AuthedJsonStringRequest(
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
                    public void onComplete(@NonNull Task<String> task) {
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

    private Task<Void> refreshAccessToken() {
        return executeRequest(Request.Method.POST, "auth/newAccessToken", null, false, true)
                .continueWithTask(new Continuation<String, Task<Void>>() {
                    @Override
                    public Task<Void> then(@NonNull Task<String> task) throws Exception {
                        if (!task.isSuccessful()) {
                            return Tasks.forException(task.getException());
                        }

                        ;
                        final String newAccessToken;
                        try {
                            final JSONObject response = new JSONObject(task.getResult());
                            newAccessToken = response.getString("accessToken");
                        } catch (final JSONException e) {
                            Log.e(TAG, "Error parsing access token response", e);

                            return Tasks.forException(new BaasException(e));
                        }

                        _auth = _auth.withNewAccessToken(newAccessToken);

                        final String authJson;
                        try {
                            authJson = _objMapper.writeValueAsString(_auth);
                        } catch (final IOException e) {
                            Log.e(TAG, "Error parsing auth response", e);
                            return Tasks.forException(new BaasException(e));
                        }

                        _preferences.edit().putString(AUTH_JWT_NAME, authJson).apply();
                        return Tasks.forResult(null);
                    }
                });
    }

    public Task<List<Object>> executePipeline(final List<PipelineStage> pipeline) {
        ensureAuthed();
        final String pipeStr;
        try {
            pipeStr = _objMapper.writeValueAsString(pipeline);
        } catch (final IOException e) {
            return Tasks.forException(e);
        }

        return executeRequest(Request.Method.POST, "pipeline", pipeStr).continueWithTask(new Continuation<String, Task<List<Object>>>() {
            @Override
            public Task<List<Object>> then(@NonNull final Task<String> task) throws Exception {
                if (task.isSuccessful()) {
                    final Document doc = Document.parse(task.getResult());
                    return Tasks.forResult((List<Object>) doc.get("result"));
                } else {
                    Log.e(TAG, "Error while executing pipeline", task.getException());
                    return Tasks.forException(task.getException());
                }
            }
        });
    }

    public Task<List<Object>> executePipeline(final PipelineStage... stages) {
        return executePipeline(Arrays.asList(stages));
    }
}
