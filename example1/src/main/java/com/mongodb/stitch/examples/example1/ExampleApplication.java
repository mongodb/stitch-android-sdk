package com.mongodb.stitch.examples.example1;

import android.app.Application;
import android.util.Log;

import com.mongodb.stitch.android.core.Stitch;
import com.mongodb.stitch.core.StitchAppClientConfiguration;

import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;

public class ExampleApplication extends Application {

    private static final String TAG = ExampleApplication.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            Stitch.initialize(getApplicationContext());
        } catch (final Exception e) {
            Log.e(TAG, "Error automatically initializing the MongoDB Stitch SDK", e);
        }

        StitchAppClientConfiguration.Builder appClientConfigBuilder = StitchAppClientConfiguration.Builder
                .forApp("stitch-tests-android-sdk-rqopr");

        CodecRegistry customCodecRegistry = CodecRegistries.fromCodecs(
                new TodoItem.Codec()
        );
        appClientConfigBuilder.withCustomCodecs(customCodecRegistry);

        try {
            Stitch.initializeDefaultAppClient(appClientConfigBuilder);
        } catch (final Exception e) {
            Log.e(TAG, "Failed to initialized Stitch app client");
        }
    }
}
