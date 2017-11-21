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
import com.mongodb.stitch.android.auth.apiKey.APIKey;

import org.bson.Document;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Authentication resource
 */
public class Auth {
    private static final String TAG = "Stitch.Auth";

    private static final class Paths {
        private static final String AUTH = "auth";
        private static final String USER_PROFILE = AUTH + "/profile";
        private static final String USER_PROFILE_API_KEYS = AUTH + "/api_keys";
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

    AuthInfo getAuthInfo() {
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

    /**
     * Create an api key associated with this user
     * @param name name of new api key
     * @return task with new api key
     */
    public Task<APIKey> createApiKey(@NonNull final String name) {
        return _stitchClient.executeRequest(
                Request.Method.POST,
                Paths.USER_PROFILE_API_KEYS,
                new Document("name", name).toJson(),
                true,
                true
        ).continueWith(new Continuation<String, APIKey>() {
            @Override
            public APIKey then(@NonNull final Task<String> task) throws Exception {
                if (task.isSuccessful()) {
                    return _objMapper.readValue(task.getResult(), APIKey.class);
                } else {
                    Log.e(TAG, "Error while creating user api key", task.getException());
                    throw task.getException();
                }
            }
        });
    }

    /**
     * Fetch an api key associated with this user by its id
     * @param id id of this user
     * @return api key for this id
     */
    public Task<APIKey> fetchApiKey(@NonNull final String id) {
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
        ).continueWith(new Continuation<String, APIKey>() {
            @Override
            public APIKey then(@NonNull final Task<String> task) throws Exception {
                if (task.isSuccessful()) {
                    return _objMapper.readValue(task.getResult(), APIKey.class);
                } else {
                    Log.e(TAG, "Error while fetching user api keys", task.getException());
                    throw task.getException();
                }
            }
        });
    }

    /**
     * Fetch all api keys associated with this user
     * @return list of api keys
     */
    public Task<List<APIKey>> fetchApiKeys() {
        return _stitchClient.executeRequest(
                Request.Method.GET,
                Paths.USER_PROFILE_API_KEYS,
                null,
                true,
                true
        ).continueWith(new Continuation<String, List<APIKey>>() {
            @Override
            public List<APIKey> then(@NonNull final Task<String> task) throws Exception {
                if (task.isSuccessful()) {
                    return Arrays.asList(_objMapper.readValue(task.getResult(), APIKey[].class));
                } else {
                    Log.e(TAG, "Error while fetching user api keys", task.getException());
                    throw task.getException();
                }
            }
        });
    }

    /**
     * Delete an api key associated with this user
     * @param id id to delete
     * @return success boolean
     */
    public Task<Boolean> deleteApiKey(@NonNull final String id) {
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
                    Log.e(TAG, "Error while deleting user api key", task.getException());
                    throw task.getException();
                }
            }
        });
    }

    /**
     * Enable or disable an api key associated with this user
     * @param id id of api key
     * @param shouldEnable whether or not the api key should be enabled
     * @return success boolean
     */
    private Task<Boolean> _enableDisableApiKey(@NonNull final String id,
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

    /**
     * Enable an api key associated with this user
     * @param id id of api key
     * @return success boolean
     */
    public Task<Boolean> enableApiKey(@NonNull final String id) {
        return _enableDisableApiKey(id, true);
    }

    /**
     * Disable an api key associated with this user
     * @param id id of api key
     * @return success boolean
     */
    public Task<Boolean> disableApiKey(@NonNull final String id) {
        return _enableDisableApiKey(id, false);
    }
}
