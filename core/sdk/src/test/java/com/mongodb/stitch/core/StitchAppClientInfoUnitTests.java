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

  @Test
  public void testStitchAppClientInfoInit() {
    final String clientAppId = "my_app-12345";
    final String dataDirectory = "/srv/mongodb/stitch";
    final String localAppName = "myApp";
    final String localAppVersion = "1.0";

    final StitchAppClientInfo stitchAppClientInfo =
        new StitchAppClientInfo(
            clientAppId,
            dataDirectory,
            localAppName,
            localAppVersion,
            BsonUtils.DEFAULT_CODEC_REGISTRY,
            null,
            null);

    assertEquals(stitchAppClientInfo.getClientAppId(), clientAppId);
    assertEquals(stitchAppClientInfo.getDataDirectory(), dataDirectory);
    assertEquals(stitchAppClientInfo.getLocalAppName(), localAppName);
    assertEquals(stitchAppClientInfo.getLocalAppVersion(), localAppVersion);
    assertEquals(
        stitchAppClientInfo.getCodecRegistry(), BsonUtils.DEFAULT_CODEC_REGISTRY);
  }
}
