package com.mongodb.stitch.android.examples.snippets;

import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.Stitch;
import com.mongodb.stitch.android.core.StitchAppClient;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoDatabase;
import com.mongodb.stitch.android.testutils.BaseStitchAndroidIntTest;
import com.mongodb.stitch.core.admin.apps.AppResponse;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteCountOptions;

import org.bson.BsonRegularExpression;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RemoteMongoCollectionExamples extends BaseStitchAndroidIntTest {
    private static final String TAG = "RemoteMongoCollectionExamples";

    private StitchAppClient appClient;

    @Before
    public void setup() {
        appClient = getAppClient(new AppResponse("reference-bqxza", "reference-bqxza", "reference-bqxza"));
    }

    @Test
    public void RemoteMongoCollection() {
        // Note: log in first -- see StitchAuth
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

    @Test
    public void RemoteMongoCollection_count_Bson_RemoteCountOptions() {
        // Get the Atlas client.
        RemoteMongoClient mongoClient = appClient.getServiceClient(RemoteMongoClient.factory, "mongodb-atlas");
        RemoteMongoDatabase db = mongoClient.getDatabase("video");
        RemoteMongoCollection<Document> movieDetails = db.getCollection("movieDetails");

        // Count all documents where title matches a regex up to a limit
        movieDetails.count(
                new Document().append("title", new BsonRegularExpression("^A")),
                new RemoteCountOptions().limit(25)).addOnCompleteListener(new OnCompleteListener<Long>() {
            @Override
            public void onComplete(@android.support.annotation.NonNull Task<Long> task) {
                if (!task.isSuccessful()) {
                    Log.e(TAG, "Count failed", task.getException());
                    return;
                }
                Log.i(TAG, "Count is " + task.getResult());
            }
        });

    }
}
