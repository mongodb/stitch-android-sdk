package com.mongodb.stitch.core;

import com.mongodb.stitch.core.internal.common.MemoryStorage;
import com.mongodb.stitch.core.internal.common.Storage;
import com.mongodb.stitch.core.internal.net.Request;
import com.mongodb.stitch.core.internal.net.Response;
import com.mongodb.stitch.core.internal.net.Transport;
import com.mongodb.stitch.core.testutil.CustomType;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecRegistries;
import org.junit.Test;

public class StitchAppClientConfigurationUnitTests {
    private static final String CLIENT_APP_ID = "foo";
    private static final String LOCAL_APP_VERSION = "bar";
    private static final String LOCAL_APP_NAME = "baz";
    private static final String BASE_URL = "qux";
    private static final Storage STORAGE = new MemoryStorage();
    private static final Transport TRANSPORT = (Request request) ->
        new Response(200, null, null);

    @Test
    public void testStitchAppClientConfigurationBuilderInit() {
        final StitchAppClientConfiguration.Builder builder =
                new StitchAppClientConfiguration.Builder();

        try {
            builder.build();
            fail();
        } catch (final IllegalArgumentException ignored) {}

        builder.withClientAppId(CLIENT_APP_ID)
                .withLocalAppVersion(LOCAL_APP_VERSION)
                .withLocalAppName(LOCAL_APP_NAME);

        try {
            builder.build();
            fail();
        } catch (final IllegalArgumentException ignored) {}

        builder.withBaseURL(BASE_URL);

        try {
            builder.build();
            fail();
        } catch (final IllegalArgumentException ignored) {}

        builder.withStorage(STORAGE);

        try {
            builder.build();
            fail();
        } catch (final IllegalArgumentException ignored) {}

        builder.withTransport(TRANSPORT);

        final StitchAppClientConfiguration config = builder.build();

        assertEquals(config.getClientAppId(), CLIENT_APP_ID);
        assertEquals(config.getLocalAppVersion(), LOCAL_APP_VERSION);
        assertEquals(config.getLocalAppName(), LOCAL_APP_NAME);
        assertEquals(config.getBaseURL(), BASE_URL);
        assertEquals(config.getStorage(), STORAGE);
        assertEquals(config.getTransport(), TRANSPORT);
        assertEquals(config.getCodecRegistry(), null);
    }

    @Test
    public void testStitchAppClientConfigurationBuilderInitWithCodecRegistry() {
        final StitchAppClientConfiguration.Builder builder =
                new StitchAppClientConfiguration.Builder();

        try {
            builder.build();
            fail();
        } catch (final IllegalArgumentException ignored) {}

        builder.withClientAppId(CLIENT_APP_ID)
                .withLocalAppVersion(LOCAL_APP_VERSION)
                .withLocalAppName(LOCAL_APP_NAME);

        try {
            builder.build();
            fail();
        } catch (final IllegalArgumentException ignored) {}

        builder.withBaseURL(BASE_URL);

        try {
            builder.build();
            fail();
        } catch (final IllegalArgumentException ignored) {}

        builder.withStorage(STORAGE);

        try {
            builder.build();
            fail();
        } catch (final IllegalArgumentException ignored) {}

        builder.withTransport(TRANSPORT);

        Codec<CustomType> customTypeCodec = new CustomType.Codec();
        builder.withCustomCodecs(CodecRegistries.fromCodecs(customTypeCodec));

        final StitchAppClientConfiguration config = builder.build();

        assertEquals(config.getClientAppId(), CLIENT_APP_ID);
        assertEquals(config.getLocalAppVersion(), LOCAL_APP_VERSION);
        assertEquals(config.getLocalAppName(), LOCAL_APP_NAME);
        assertEquals(config.getBaseURL(), BASE_URL);
        assertEquals(config.getStorage(), STORAGE);
        assertEquals(config.getTransport(), TRANSPORT);

        // Ensure that there is a codec for our custom type.
        assertEquals(config.getCodecRegistry().get(CustomType.class), customTypeCodec);

        // Ensure that configuring the custom codec merged with the default types.
        assertNotNull(config.getCodecRegistry().get(Document.class));
    }
}
