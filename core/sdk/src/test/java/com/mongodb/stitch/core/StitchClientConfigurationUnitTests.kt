/*
 * Copyright 2018-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.stitch.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

import com.mongodb.stitch.core.internal.common.BsonUtils
import com.mongodb.stitch.core.internal.common.MemoryStorage
import com.mongodb.stitch.core.internal.net.Transport
import com.mongodb.stitch.core.testutils.CustomType
import org.bson.Document
import org.bson.codecs.configuration.CodecRegistries
import org.junit.Test
import org.mockito.Mockito

class StitchClientConfigurationUnitTests {

    @Test
    fun testStitchClientConfigurationBuilder() {
        val baseUrl = "http://domain.com"
        val storage = MemoryStorage()
        val transport = Mockito.mock(Transport::class.java)

        val builder = StitchClientConfiguration.Builder()
        builder.withBaseUrl(baseUrl)
        builder.withStorage(storage)
        builder.withTransport(transport)
        builder.withDefaultRequestTimeout(1500L)
        builder.withStorage(storage)
        builder.withTransport(transport)
        builder.withStorage(storage)
        builder.withTransport(transport)
        var config = builder.build()

        assertEquals(config.baseUrl, baseUrl)
        assertEquals(config.storage, storage)
        assertEquals(config.transport, transport)
        assertEquals(
                config.defaultRequestTimeout,
                1500L)
        assertEquals(BsonUtils.DEFAULT_CODEC_REGISTRY, config.codecRegistry)

        // With a custom codec
        val customTypeCodec = CustomType.Codec()
        builder.withCodecRegistry(CodecRegistries.fromCodecs(customTypeCodec))
        config = builder.build()

        assertEquals(config.baseUrl, baseUrl)
        assertEquals(config.storage, storage)
        assertEquals(config.transport, transport)

        // Ensure that there is a codec for our custom type.
        assertEquals(config.codecRegistry.get(CustomType::class.java), customTypeCodec)

        // Ensure that configuring the custom codec merged with the default types.
        assertNotNull(config.codecRegistry.get(Document::class.java))
    }
}
