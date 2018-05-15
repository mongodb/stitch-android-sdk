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

package com.mongodb.stitch.core;

import static com.mongodb.stitch.core.testutils.Assert.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.mongodb.stitch.core.internal.common.BsonUtils;
import com.mongodb.stitch.core.internal.common.MemoryStorage;
import com.mongodb.stitch.core.internal.common.Storage;
import com.mongodb.stitch.core.internal.net.Request;
import com.mongodb.stitch.core.internal.net.Response;
import com.mongodb.stitch.core.internal.net.Transport;
import com.mongodb.stitch.core.testutils.CustomType;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistries;
import org.junit.Test;

public class StitchClientConfigurationUnitTests {

  @Test
  public void testStitchClientConfigurationBuilder() {
    final String baseUrl = "http://domain.com";
    final Storage storage = new MemoryStorage();
    final Transport transport = (Request request) -> new Response("good");

    // A minimum of baseUrl, storage, and transport must be set
    StitchClientConfiguration.Builder builder = new StitchClientConfiguration.Builder();
    assertThrows(builder::build, IllegalArgumentException.class);

    builder.withBaseUrl(baseUrl);

    assertThrows(builder::build, IllegalArgumentException.class);

    builder = new StitchClientConfiguration.Builder();
    builder.withStorage(storage);

    assertThrows(builder::build, IllegalArgumentException.class);

    builder = new StitchClientConfiguration.Builder();
    builder.withTransport(transport);

    assertThrows(builder::build, IllegalArgumentException.class);

    builder.withDefaultRequestTimeout(1500L);
    assertThrows(builder::build, IllegalArgumentException.class);

    builder = new StitchClientConfiguration.Builder();
    builder.withBaseUrl(baseUrl);
    builder.withStorage(storage);

    assertThrows(builder::build, IllegalArgumentException.class);

    builder = new StitchClientConfiguration.Builder();
    builder.withBaseUrl(baseUrl);
    builder.withTransport(transport);

    assertThrows(builder::build, IllegalArgumentException.class);

    builder = new StitchClientConfiguration.Builder();
    builder.withStorage(storage);
    builder.withTransport(transport);

    assertThrows(builder::build, IllegalArgumentException.class);

    // Minimum satisfied
    builder = new StitchClientConfiguration.Builder();
    builder.withBaseUrl(baseUrl);
    builder.withStorage(storage);
    builder.withTransport(transport);
    builder.withDefaultRequestTimeout(1500L);
    StitchClientConfiguration config = builder.build();

    assertEquals(config.getBaseUrl(), baseUrl);
    assertEquals(config.getStorage(), storage);
    assertEquals(config.getTransport(), transport);
    assertEquals(
        config.getDefaultRequestTimeout(),
        (Long) 1500L);
    assertEquals(BsonUtils.DEFAULT_CODEC_REGISTRY, config.getCodecRegistry());

    // With a custom codec
    final CustomType.Codec customTypeCodec = new CustomType.Codec();
    builder.withCodecRegistry(CodecRegistries.fromCodecs(customTypeCodec));
    config = builder.build();

    assertEquals(config.getBaseUrl(), baseUrl);
    assertEquals(config.getStorage(), storage);
    assertEquals(config.getTransport(), transport);

    // Ensure that there is a codec for our custom type.
    assertEquals(config.getCodecRegistry().get(CustomType.class), customTypeCodec);

    // Ensure that configuring the custom codec merged with the default types.
    assertNotNull(config.getCodecRegistry().get(Document.class));
  }
}
