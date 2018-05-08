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

import com.mongodb.stitch.core.internal.common.BsonUtils;
import org.junit.Test;

public class StitchAppClientInfoUnitTests {
  private static final String CLIENT_APP_ID = "foo";
  private static final String DATA_DIRECTORY = "bar";
  private static final String LOCAL_APP_NAME = "baz";
  private static final String LOCAL_APP_VERSION = "qux";

  @Test
  public void testStitchAppClientInfoInit() {
    final StitchAppClientInfo stitchAppClientInfo =
        new StitchAppClientInfo(
            CLIENT_APP_ID,
            DATA_DIRECTORY,
            LOCAL_APP_NAME,
            LOCAL_APP_VERSION,
            BsonUtils.DEFAULT_CODEC_REGISTRY);

    assertEquals(stitchAppClientInfo.getClientAppId(), CLIENT_APP_ID);
    assertEquals(stitchAppClientInfo.getDataDirectory(), DATA_DIRECTORY);
    assertEquals(stitchAppClientInfo.getLocalAppName(), LOCAL_APP_NAME);
    assertEquals(stitchAppClientInfo.getLocalAppVersion(), LOCAL_APP_VERSION);
    assertEquals(
        stitchAppClientInfo.getConfiguredCodecRegistry(), BsonUtils.DEFAULT_CODEC_REGISTRY);
  }
}
