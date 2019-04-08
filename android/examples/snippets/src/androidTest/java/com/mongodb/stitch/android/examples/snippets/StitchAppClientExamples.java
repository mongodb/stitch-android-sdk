package com.mongodb.stitch.android.examples.snippets;

import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.mongodb.lang.NonNull;
import com.mongodb.stitch.android.core.Stitch;
import com.mongodb.stitch.android.core.StitchAppClient;
import com.mongodb.stitch.android.core.auth.StitchUser;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoDatabase;
import com.mongodb.stitch.android.testutils.BaseStitchAndroidIntTest;
import com.mongodb.stitch.core.admin.apps.AppResponse;
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential;

import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StitchAppClientExamples extends BaseStitchAndroidIntTest {
    private static final String TAG = "StitchClientExamples";

    private StitchAppClient appClient;

    @Before
    public void setup() {
        appClient = getAppClient(new AppResponse("reference-bqxza", "reference-bqxza", "reference-bqxza"));
    }

    @Test
    public void StitchAppClient() {
        // Get the default app client. This is automatically initialized by Stitch
        // if there is a `stitch_client_app_id` value in the values/strings.xml file.

        // StitchAppClient appClient = Stitch.getDefaultAppClient();

        // Log in anonymously. Some form of login is required before we can read documents.
        appClient.getAuth().loginWithCredential(new AnonymousCredential()).addOnCompleteListener(new OnCompleteListener<StitchUser>() {
            @Override
            public void onComplete(@NonNull Task<StitchUser> task) {
                if (!task.isSuccessful()) {
                    Log.e(TAG, "Failed to log in!", task.getException());
                    return;
                }

                // Get the Atlas client.
                RemoteMongoClient mongoClient = appClient.getServiceClient(RemoteMongoClient.factory, "mongodb-atlas");
                RemoteMongoDatabase db = mongoClient.getDatabase("video");
                RemoteMongoCollection<Document> movieDetails = db.getCollection("movieDetails");

                // Find 20 documents
                movieDetails.find()
                        .projection(new Document().append("title", 1).append("year", 1))
                        .limit(20)
                        .forEach(document -> {
                            // Print documents to the log.
                            Log.i(TAG, "Got document: " + document.toString());
                        });
            }
        });

    }
}
