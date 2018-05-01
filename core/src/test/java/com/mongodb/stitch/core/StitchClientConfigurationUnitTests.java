package com.mongodb.stitch.core;

import com.mongodb.stitch.core.internal.common.MemoryStorage;
import com.mongodb.stitch.core.internal.common.Storage;
import com.mongodb.stitch.core.internal.net.Request;
import com.mongodb.stitch.core.internal.net.Response;
import com.mongodb.stitch.core.internal.net.Transport;
import com.mongodb.stitch.core.testutil.CustomType;

import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistries;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class StitchClientConfigurationUnitTests {
    private final String baseURL = "qux";
    private final Storage storage = new MemoryStorage();
    private final Transport transport = (Request request) ->
            new Response(200, null, null);

    @Test
    public void testStitchClientConfigurationBuilderImplInit() {
        final StitchClientConfiguration.Builder builder =
                new StitchClientConfiguration.Builder();

        try {
            builder.build();
            fail();
        } catch (final IllegalArgumentException ignored) {}

        builder.withBaseURL(this.baseURL);

        try {
            builder.build();
            fail();
        } catch (final IllegalArgumentException ignored) {}

        builder.withStorage(this.storage);

        try {
            builder.build();
            fail();
        } catch (final IllegalArgumentException ignored) {}

        builder.withTransport(this.transport);

        final StitchClientConfiguration config = builder.build();

        assertEquals(config.getBaseURL(), this.baseURL);
        assertEquals(config.getStorage(), this.storage);
        assertEquals(config.getTransport(), this.transport);
        assertEquals(config.getCodecRegistry(), null);
    }

    @Test
    public void testStitchClientConfigurationBuilderImplInitWithCodecRegistry() {
        final StitchClientConfiguration.Builder builder =
                new StitchClientConfiguration.Builder();

        try {
            builder.build();
            fail();
        } catch (final IllegalArgumentException ignored) {}

        builder.withBaseURL(this.baseURL);

        try {
            builder.build();
            fail();
        } catch (final IllegalArgumentException ignored) {}

        builder.withStorage(this.storage);

        try {
            builder.build();
            fail();
        } catch (final IllegalArgumentException ignored) {}

        builder.withTransport(this.transport);

        CustomType.Codec customTypeCodec = new CustomType.Codec();

        builder.withCustomCodecs(CodecRegistries.fromCodecs(
                customTypeCodec
        ));

        final StitchClientConfiguration config = builder.build();

        assertEquals(config.getBaseURL(), this.baseURL);
        assertEquals(config.getStorage(), this.storage);
        assertEquals(config.getTransport(), this.transport);

        // Ensure that there is a codec for our custom type.
        assertEquals(config.getCodecRegistry().get(CustomType.class), customTypeCodec);

        // Ensure that configuring the custom codec merged with the default types.
        assertNotNull(config.getCodecRegistry().get(Document.class));
    }
}
