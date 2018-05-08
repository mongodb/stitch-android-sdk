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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.mongodb.stitch.core.internal.common.MemoryStorage;
import com.mongodb.stitch.core.internal.common.Storage;
import com.mongodb.stitch.core.internal.net.Request;
import com.mongodb.stitch.core.internal.net.Response;
import com.mongodb.stitch.core.internal.net.Transport;
import com.mongodb.stitch.core.testutil.CustomType;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistries;
import org.junit.Test;

public class StitchClientConfigurationUnitTests {
  private final String baseUrl = "qux";
  private final Storage storage = new MemoryStorage();
  private final Transport transport = (Request request) -> new Response(200, null, null);

  @Test
  public void testStitchClientConfigurationBuilderImplInit() {
    final StitchClientConfiguration.Builder builder = new StitchClientConfiguration.Builder();

    try {
      builder.build();
      fail();
    } catch (final IllegalArgumentException ignored) {
      // do nothing
    }

    builder.withBaseUrl(this.baseUrl);

    try {
      builder.build();
      fail();
    } catch (final IllegalArgumentException ignored) {
      // do nothing
    }

    builder.withStorage(this.storage);

    try {
      builder.build();
      fail();
    } catch (final IllegalArgumentException ignored) {
      // do nothing
    }

    builder.withTransport(this.transport);

    final StitchClientConfiguration config = builder.build();

    assertEquals(config.getBaseUrl(), this.baseUrl);
    assertEquals(config.getStorage(), this.storage);
    assertEquals(config.getTransport(), this.transport);
    assertEquals(config.getCodecRegistry(), null);
  }

  @Test
  public void testStitchClientConfigurationBuilderImplInitWithCodecRegistry() {
    final StitchClientConfiguration.Builder builder = new StitchClientConfiguration.Builder();

    try {
      builder.build();
      fail();
    } catch (final IllegalArgumentException ignored) {
      // do nothing
    }

    builder.withBaseUrl(this.baseUrl);

    try {
      builder.build();
      fail();
    } catch (final IllegalArgumentException ignored) {
      // do nothing
    }

    builder.withStorage(this.storage);

    try {
      builder.build();
      fail();
    } catch (final IllegalArgumentException ignored) {
      // do nothing
    }

    builder.withTransport(this.transport);

    CustomType.Codec customTypeCodec = new CustomType.Codec();

    builder.withCustomCodecs(CodecRegistries.fromCodecs(customTypeCodec));

    final StitchClientConfiguration config = builder.build();

    assertEquals(config.getBaseUrl(), this.baseUrl);
    assertEquals(config.getStorage(), this.storage);
    assertEquals(config.getTransport(), this.transport);

    // Ensure that there is a codec for our custom type.
    assertEquals(config.getCodecRegistry().get(CustomType.class), customTypeCodec);

    // Ensure that configuring the custom codec merged with the default types.
    assertNotNull(config.getCodecRegistry().get(Document.class));
  }
}
