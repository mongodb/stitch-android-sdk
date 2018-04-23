package com.mongodb.stitch.core;

import com.mongodb.stitch.core.internal.common.BSONUtils;

import org.bson.codecs.configuration.CodecRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StitchAppClientInfoUnitTests {
    private static final String CLIENT_APP_ID = "foo";
    private static final String DATA_DIRECTORY = "bar";
    private static final String LOCAL_APP_NAME = "baz";
    private static final String LOCAL_APP_VERSION = "qux";

    @Test
    void testStitchAppClientInfoInit() {
        final StitchAppClientInfo stitchAppClientInfo = new StitchAppClientInfo(
                CLIENT_APP_ID,
                DATA_DIRECTORY,
                LOCAL_APP_NAME,
                LOCAL_APP_VERSION,
                BSONUtils.DEFAULT_CODEC_REGISTRY
        );

        assertEquals(stitchAppClientInfo.clientAppId, CLIENT_APP_ID);
        assertEquals(stitchAppClientInfo.dataDirectory, DATA_DIRECTORY);
        assertEquals(stitchAppClientInfo.localAppName, LOCAL_APP_NAME);
        assertEquals(stitchAppClientInfo.localAppVersion, LOCAL_APP_VERSION);
        assertEquals(stitchAppClientInfo.configuredCodecRegistry, BSONUtils.DEFAULT_CODEC_REGISTRY);
    }
}
