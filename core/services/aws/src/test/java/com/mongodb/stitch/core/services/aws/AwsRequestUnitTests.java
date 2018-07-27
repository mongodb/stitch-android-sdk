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

package com.mongodb.stitch.core.services.aws;

import static com.mongodb.stitch.core.testutils.Assert.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.bson.Document;
import org.junit.Test;

public class AwsRequestUnitTests {

  @Test
  public void testBuilder() {

    // Require at a minimum service and action
    assertThrows(() -> new AwsRequest.Builder().build(), IllegalArgumentException.class);
    assertThrows(() -> new AwsRequest.Builder()
        .withService("ses").build(), IllegalArgumentException.class);
    assertThrows(() -> new AwsRequest.Builder()
        .withAction("send").build(), IllegalArgumentException.class);

    // Minimum satisfied
    final String expectedService = "ses";
    final String expectedAction = "send";
    final AwsRequest request = new AwsRequest.Builder()
        .withService(expectedService).withAction(expectedAction).build();
    assertEquals(expectedService, request.getService());
    assertEquals(expectedAction, request.getAction());
    assertNull(request.getRegion());
    assertTrue(request.getArguments().isEmpty());

    final String expectedRegion = "us-east-1";
    final Document expectedArgs = new Document("hi", "hello");
    final AwsRequest fullRequest = new AwsRequest.Builder()
        .withService(expectedService)
        .withAction(expectedAction)
        .withRegion(expectedRegion)
        .withArguments(expectedArgs)
        .build();
    assertEquals(expectedService, fullRequest.getService());
    assertEquals(expectedAction, fullRequest.getAction());
    assertEquals(expectedRegion, fullRequest.getRegion());
    assertEquals(expectedArgs, fullRequest.getArguments());
  }
}
