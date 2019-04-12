package com.mongodb.stitch.android.examples.snippets;

import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import com.mongodb.stitch.android.core.Stitch;
import com.mongodb.stitch.android.core.StitchAppClient;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoDatabase;
import com.mongodb.stitch.android.testutils.BaseStitchAndroidIntTest;
import com.mongodb.stitch.core.admin.apps.AppResponse;

import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RemoteMongoDatabaseExamples extends BaseStitchAndroidIntTest {
    private static final String TAG = "RemoteMongoDatabaseExamples";

    private StitchAppClient appClient;

    @Before
    public void setup() {
        appClient = getAppClient(new AppResponse("reference-bqxza", "reference-bqxza", "reference-bqxza"));
    }

    @Test
    public void RemoteMongoDatabase() {
        // Find 20 documents in a collection
        // Note: log in first -- see StitchAuth
        RemoteMongoClient mongoClient = appClient.getServiceClient(RemoteMongoClient.factory, "mongodb-atlas");
        RemoteMongoDatabase db = mongoClient.getDatabase("video");
        RemoteMongoCollection<Document> movieDetails = db.getCollection("movieDetails");
        movieDetails.find().limit(20).forEach(document -> {
            Log.i(TAG, "Got document: " + document.toString());
        });
    }
}
