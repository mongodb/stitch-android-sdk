package com.mongodb.stitch.core;

import com.mongodb.stitch.core.internal.common.BSONUtils;
import com.mongodb.stitch.core.internal.common.MemoryStorage;
import com.mongodb.stitch.core.internal.common.Storage;
import com.mongodb.stitch.core.internal.net.Request;
import com.mongodb.stitch.core.internal.net.Response;
import com.mongodb.stitch.core.internal.net.Transport;
import com.mongodb.stitch.core.testutil.CustomType;

import org.bson.BsonReader;
import org.bson.BsonValue;
import org.bson.BsonWriter;
import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.CollectibleCodec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistries;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StitchClientConfigurationUnitTests {
    private final String baseURL = "qux";
    private final Storage storage = new MemoryStorage();
    private final Transport transport = (Request request) ->
            new Response(200, null, null);
    private final Long transportTimeout = 15000L;

    @Test
    void testStitchClientConfigurationBuilderImplInit() {
        final StitchClientConfiguration.Builder builder =
                new StitchClientConfiguration.Builder();

        assertThrows(IllegalArgumentException.class, builder::build);

        builder.withBaseURL(this.baseURL);

        assertThrows(IllegalArgumentException.class, builder::build);

        builder.withStorage(this.storage);

        assertThrows(IllegalArgumentException.class, builder::build);

        builder.withTransport(this.transport);

        assertThrows(IllegalArgumentException.class, builder::build);

        builder.withTransportTimeout(this.transportTimeout);

        final StitchClientConfiguration config = builder.build();

        assertEquals(config.getBaseURL(), this.baseURL);
        assertEquals(config.getStorage(), this.storage);
        assertEquals(config.getTransport(), this.transport);
        assertEquals(config.getTransportTimeout(), this.transportTimeout);
        assertEquals(config.getCodecRegistry(), null);
    }

    @Test
    void testStitchClientConfigurationBuilderImplInitWithCodecRegistry() {
        final StitchClientConfiguration.Builder builder =
                new StitchClientConfiguration.Builder();

        assertThrows(IllegalArgumentException.class, builder::build);

        builder.withBaseURL(this.baseURL);

        assertThrows(IllegalArgumentException.class, builder::build);

        builder.withStorage(this.storage);

        assertThrows(IllegalArgumentException.class, builder::build);

        builder.withTransport(this.transport);

        assertThrows(IllegalArgumentException.class, builder::build);

        builder.withTransportTimeout(this.transportTimeout);

        CustomType.Codec customTypeCodec = new CustomType.Codec();

        builder.withCustomCodecs(CodecRegistries.fromCodecs(
                customTypeCodec
        ));

        final StitchClientConfiguration config = builder.build();

        assertEquals(config.getBaseURL(), this.baseURL);
        assertEquals(config.getStorage(), this.storage);
        assertEquals(config.getTransport(), this.transport);
        assertEquals(config.getTransportTimeout(), this.transportTimeout);

        // Ensure that there is a codec for our custom type.
        assertEquals(config.getCodecRegistry().get(CustomType.class), customTypeCodec);

        // Ensure that configuring the custom codec merged with the default types.
        assertNotNull(config.getCodecRegistry().get(Document.class));

    }
}
