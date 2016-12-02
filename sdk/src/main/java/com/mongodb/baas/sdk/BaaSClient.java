package com.mongodb.baas.sdk;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.mongodb.baas.sdk.auth.Auth;
import com.mongodb.baas.sdk.auth.AuthProvider;
import com.mongodb.baas.sdk.auth.AuthProviderInfo;
import com.mongodb.baas.sdk.auth.facebook.FacebookAuthProviderInfo;

import org.bson.Document;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static com.mongodb.baas.sdk.Volley.*;

public class BaaSClient {
    private static final String TAG = "BaaS";
    private static final String DEFAULT_BASE_URL = "http://localhost:8080";

    private final String _baseUrl;
    private final String _appName;
    private final RequestQueue _queue;
    private final ObjectMapper _objMapper;

    private Auth _auth;

    public BaaSClient(final Context context, final String appName, final String baseUrl) {
        _appName = appName;
        _queue = Volley.newRequestQueue(context);

        _objMapper = new ObjectMapper();
        SimpleModule docModule = new SimpleModule("docModule", new Version(1, 0, 0, null));
        docModule.addSerializer(Document.class, new JsonSerializer<Document>() {
            @Override
            public void serialize(
                    final Document value,
                    final JsonGenerator jgen,
                    final SerializerProvider provider
            ) throws IOException {
                jgen.writeRawValue(value.toJson());
            }
        }); // assuming serializer declares correct class to bind to
        _objMapper.registerModule(docModule);

        _baseUrl = baseUrl;
    }

    public BaaSClient(final Context context, final String appName) {
        this(context, appName, DEFAULT_BASE_URL);
    }

    // TODO(erd): Error if no auth...
    public Auth getAuth() {
        return _auth;
    }

    public boolean isAuthed() {
        return _auth != null;
    }

    public Task<AuthProviderInfo> getAuthProviders() {

        final TaskCompletionSource<AuthProviderInfo> future = new TaskCompletionSource<>();
        final String url = String.format("%s/v1/app/%s/auth", _baseUrl, _appName);

        final JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
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
                                }
                            } catch (final JSONException | IOException e) {
                                Log.e(
                                        TAG,
                                        String.format("Error while getting auth provider info for '%s'", authProviderName),
                                        e);

                                // TODO(ERD): Wrap with our own exception
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

                        // TODO(ERD): Wrap with our own exception
                        future.setException(error);
                    }
                });
        _queue.add(request);

        return future.getTask();
    }

    public Task<Void> logout() {
        return executeRequest(Request.Method.DELETE, "auth/logout", 200, null).continueWithTask(new Continuation<Object, Task<Void>>() {
            @Override
            public Task<Void> then(@NonNull final Task<Object> task) throws Exception {
                if (task.isSuccessful()) {
                    return Tasks.forResult(null);
                }
                return Tasks.forException(task.getException());
            }
        });
    }

    public Task<Auth> logInWithProvider(AuthProvider authProvider) {

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
                            // TODO(ERD): Change this to a string request?
                            _auth = _objMapper.readValue(response, Auth.class);
                            future.setResult(_auth);
                        } catch (final IOException e) {
                            Log.e(TAG, "Error parsing auth response", e);

                            // TODO(ERD): Wrap with our own exception
                            future.setException(e);
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(final VolleyError error) {
                        Log.e(TAG, "Error while logging in with auth provider", error);

                        // TODO(ERD): Wrap with our own exception
                        future.setException(error);
                    }
                });
        _queue.add(request);

        return future.getTask();
    }

    public Task<Object> executeRequest(
            final int method,
            final String resource,
            final int expectedStatusCode, // TODO(ERD): Use status code for validation
            final String body
    ) {
        final String url = String.format("%s/v1/app/%s/%s", _baseUrl, _appName, resource);

        final TaskCompletionSource<Object> future = new TaskCompletionSource<>();
        final AuthedJsonStringRequest request = new AuthedJsonStringRequest(
                method,
                url,
                body,
                Collections.singletonMap(
                        "Authorization",
                        String.format("Bearer %s", _auth.getToken())),
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(final String response) {
                        final Document doc = Document.parse(response);

                        if (doc.containsKey("error")) {
                            // TODO(ERD): Wrap with our own exception
                            future.setException(new Exception(doc.getString("error")));
                            return;
                        }

                        future.setResult(doc.get("result"));
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(final VolleyError error) {
                        Log.e(TAG, "Error while executing request", error);

                        if (error.networkResponse == null) {
                            future.setException(error);
                            return;
                        }

                        if (!error.networkResponse.headers.get("Content-Type").equals("application/json")) {

                            // TODO(ERD): Wrap with our own exception
                            future.setException(error);
                            return;
                        }

                        final String errorMsg;
                        try {
                            errorMsg = new String(
                                    error.networkResponse.data,
                                    HttpHeaderParser.parseCharset(error.networkResponse.headers, "utf-8"));
                        } catch (final UnsupportedEncodingException e) {
                            // TODO(ERD): Wrap with our own exception
                            future.setException(e);
                            return;
                        }

                        // TODO(ERD): Wrap with our own exception
                        future.setException(new Exception(errorMsg));
                    }
                });
        _queue.add(request);

        return future.getTask();
    }

    public Task<List<Object>> executePipeline(final List<PipelineStage> pipeline) {
        final String pipeStr;
        try {
            pipeStr = _objMapper.writeValueAsString(pipeline);
        } catch (final IOException e) {
            return Tasks.forException(e);
        }

        return executeRequest(Request.Method.POST, "pipeline", 200, pipeStr).continueWithTask(new Continuation<Object, Task<List<Object>>>() {
            @Override
            public Task<List<Object>> then(@NonNull final Task<Object> task) throws Exception {
                if (task.isSuccessful()) {
                    return Tasks.forResult((List<Object>) task.getResult());
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
