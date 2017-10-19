package com.mongodb.stitch.android;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.android.volley.Request;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.auth.AuthInfo;
import com.mongodb.stitch.android.auth.UserProfile;
import com.mongodb.stitch.android.auth.apiKey.ApiKey;

import org.bson.Document;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Created by jasonflax on 10/18/17.
 */
public class Auth {
    private static final String TAG = "Stitch.Auth";

    private static final class Paths {
        private static final String AUTH = "auth";
        private static final String USER_PROFILE = AUTH + "/me";
        private static final String USER_PROFILE_API_KEYS = USER_PROFILE + "/api_keys";
    }

    @NonNull
    private final StitchClient _stitchClient;
    @NonNull
    private final AuthInfo _authInfo;
    @Nullable
    private UserProfile _userProfile;
    private final ObjectMapper _objMapper;

    Auth(@NonNull final StitchClient stitchClient,
         @NonNull final AuthInfo authInfo) {
        this._stitchClient = stitchClient;
        this._authInfo = authInfo;
        this._objMapper = CustomObjectMapper.createObjectMapper();
    }

    public AuthInfo getAuthInfo() {
        return this._authInfo;
    }

    /**
     * Fetch the current user profile
     *
     * @return profile of the given user
     */
    public Task<UserProfile> getUserProfile() {
        return _stitchClient.executeRequest(
                Request.Method.GET,
                Paths.USER_PROFILE
        ).continueWith(new Continuation<String, UserProfile>() {
            @Override
            public UserProfile then(@NonNull Task<String> task) throws Exception {
                if (!task.isSuccessful()) {
                    throw task.getException();
                }

                try {
                    _userProfile = _objMapper.readValue(task.getResult(), UserProfile.class);
                } catch (final IOException e) {
                    Log.e(TAG, "Error parsing user response", e);
                    throw e;
                }

                return _userProfile;
            }
        });
    }

    public Task<ApiKey> createSelfApiKey(@NonNull final String name) {
        return _stitchClient.executeRequest(
                Request.Method.POST,
                Paths.USER_PROFILE_API_KEYS,
                new Document("name", name).toJson(),
                true,
                true
        ).continueWith(new Continuation<String, ApiKey>() {
            @Override
            public ApiKey then(@NonNull final Task<String> task) throws Exception {
                if (task.isSuccessful()) {
                    return _objMapper.readValue(task.getResult(), ApiKey.class);
                } else {
                    Log.e(TAG, "Error while creating self api key", task.getException());
                    throw task.getException();
                }
            }
        });
    }

    public Task<ApiKey> fetchSelfApiKey(@NonNull final String id) {
        return _stitchClient.executeRequest(
                Request.Method.GET,
                String.format(
                        "%s/%s",
                        Paths.USER_PROFILE_API_KEYS,
                        id
                ),
                null,
                true,
                true
        ).continueWith(new Continuation<String, ApiKey>() {
            @Override
            public ApiKey then(@NonNull final Task<String> task) throws Exception {
                if (task.isSuccessful()) {
                    return _objMapper.readValue(task.getResult(), ApiKey.class);
                } else {
                    Log.e(TAG, "Error while fetching self api keys", task.getException());
                    throw task.getException();
                }
            }
        });
    }

    public Task<List<ApiKey>> fetchSelfApiKeys() {
        return _stitchClient.executeRequest(
                Request.Method.GET,
                Paths.USER_PROFILE_API_KEYS,
                null,
                true,
                true
        ).continueWith(new Continuation<String, List<ApiKey>>() {
            @Override
            public List<ApiKey> then(@NonNull final Task<String> task) throws Exception {
                if (task.isSuccessful()) {
                    return Arrays.asList(_objMapper.readValue(task.getResult(), ApiKey[].class));
                } else {
                    Log.e(TAG, "Error while fetching self api keys", task.getException());
                    throw task.getException();
                }
            }
        });
    }

    public Task<Boolean> deleteSelfApiKey(@NonNull final String id) {
        return _stitchClient.executeRequest(
                Request.Method.DELETE,
                String.format(
                        "%s/%s",
                        Paths.USER_PROFILE_API_KEYS,
                        id
                ),
                null,
                true,
                true
        ).continueWith(new Continuation<String, Boolean>() {
            @Override
            public Boolean then(@NonNull final Task<String> task) throws Exception {
                if (task.isSuccessful()) {
                    return true;
                } else {
                    Log.e(TAG, "Error while deleting self api key", task.getException());
                    throw task.getException();
                }
            }
        });
    }

    private Task<Boolean> _enableDisableSelfApiKey(@NonNull final String id,
                                                   final boolean shouldEnable) {
        return _stitchClient.executeRequest(
                Request.Method.PUT,
                String.format(
                        "%s/%s/%s",
                        Paths.USER_PROFILE_API_KEYS,
                        id,
                        shouldEnable ? "enable" : "disable"
                ),
                null,
                true,
                true
        ).continueWith(new Continuation<String, Boolean>() {
            @Override
            public Boolean then(@NonNull final Task<String> task) throws Exception {
                if (task.isSuccessful()) {
                    return true;
                } else {
                    Log.e(
                            TAG,
                            String.format(
                                    "Error while %s user api key",
                                    shouldEnable ? "enabling" : "disabling"
                            ),
                            task.getException()
                    );
                    throw task.getException();
                }
            }
        });
    }

    public Task<Boolean> enableSelfApiKey(@NonNull final String id) {
        return _enableDisableSelfApiKey(id, true);
    }

    public Task<Boolean> disableSelfApiKey(@NonNull final String id) {
        return _enableDisableSelfApiKey(id, false);
    }
}
