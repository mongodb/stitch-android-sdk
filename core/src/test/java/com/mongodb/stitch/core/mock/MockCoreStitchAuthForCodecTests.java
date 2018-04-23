package com.mongodb.stitch.core.mock;

import com.mongodb.stitch.core.auth.internal.CoreStitchAuth;
import com.mongodb.stitch.core.auth.internal.CoreStitchUserImpl;
import com.mongodb.stitch.core.auth.internal.StitchUserFactory;
import com.mongodb.stitch.core.auth.internal.StitchUserProfileImpl;
import com.mongodb.stitch.core.internal.common.MemoryStorage;

import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;


public final class MockCoreStitchAuthForCodecTests extends CoreStitchAuth<CoreStitchUserImpl> {
    @Override
    protected Document getDeviceInfo() {
        return null;
    }

    @Override
    protected StitchUserFactory<CoreStitchUserImpl> getUserFactory() {
        return (String id,
                String loggedInProviderType,
                String loggedInProviderName,
                StitchUserProfileImpl userProfile) ->
                new CoreStitchUserImpl(
                        id,
                        loggedInProviderType,
                        loggedInProviderName,
                        userProfile
                ) { };
    }

    @Override
    protected void onAuthEvent() {

    }

    @Override
    public synchronized boolean isLoggedIn() {
        return true; // We want to override auth for the codec tests
    }

    // Constructor for default codec registry
    public MockCoreStitchAuthForCodecTests(MockRequestClientForCodecTests mockRequestClient) {
        super(
                mockRequestClient,
                null,
                new MemoryStorage(),
                null
        );
    }

    // Constructor for custom codec registry
    public MockCoreStitchAuthForCodecTests(MockRequestClientForCodecTests mockRequestClient,
                       CodecRegistry configuredCustomCodecRegistry) {
        super(
                mockRequestClient,
                null,
                new MemoryStorage(),
                configuredCustomCodecRegistry
        );
    }
}
