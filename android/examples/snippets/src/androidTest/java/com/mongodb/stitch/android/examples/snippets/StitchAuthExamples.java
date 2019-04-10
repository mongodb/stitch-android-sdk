package com.mongodb.stitch.android.examples.snippets;

import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.mongodb.lang.NonNull;
import com.mongodb.stitch.android.core.StitchAppClient;
import com.mongodb.stitch.android.core.auth.StitchAuth;
import com.mongodb.stitch.android.core.auth.StitchUser;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoDatabase;
import com.mongodb.stitch.android.testutils.BaseStitchAndroidIntTest;
import com.mongodb.stitch.core.admin.apps.AppResponse;
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteCountOptions;

import org.bson.BsonRegularExpression;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StitchAuthExamples extends BaseStitchAndroidIntTest {
    private static final String TAG = "StitchAuthExamples";

    private StitchAppClient appClient;

    @Before
    public void setup() {
        appClient = getAppClient(new AppResponse("reference-bqxza", "reference-bqxza", "reference-bqxza"));
    }

    @Test
    public void loginWithCredential() {
        StitchAuth auth = appClient.getAuth();

        // The Stitch app is configured for Anonymous login in the Stitch UI
        auth.loginWithCredential(new AnonymousCredential()).addOnCompleteListener(new OnCompleteListener<StitchUser>() {
            @Override
            public void onComplete(@NonNull Task<StitchUser> task) {
                if (task.isSuccessful()) {
                    Log.i(TAG, "Anonymous login successful!");
                } else {
                    Log.e(TAG, "Anonymous login failed!", task.getException());
                }
            }
        });
    }

    @Test
    public void logout() {
        StitchAuth auth = appClient.getAuth();
        auth.logout().addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                    Log.i(TAG, "Successfully logged out!");
                } else {
                    Log.e(TAG, "Logout failed!", task.getException());
                }
            }
        });
    }
}
