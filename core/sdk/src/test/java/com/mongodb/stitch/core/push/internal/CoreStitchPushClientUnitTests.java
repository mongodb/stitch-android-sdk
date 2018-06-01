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

package com.mongodb.stitch.core.push.internal;

import static com.mongodb.stitch.core.testutils.Assert.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient;
import com.mongodb.stitch.core.internal.net.Method;
import com.mongodb.stitch.core.internal.net.StitchAuthDocRequest;
import com.mongodb.stitch.core.internal.net.StitchAuthRequest;
import org.bson.Document;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class CoreStitchPushClientUnitTests {

  @Test
  public void testRegister() {
    final String serviceName = "svc1";
    final StitchPushRoutes routes = new StitchPushRoutes("foo");
    final StitchAuthRequestClient requestClient = Mockito.mock(StitchAuthRequestClient.class);
    final CoreStitchPushClient client =
        new CoreStitchPushClientImpl(requestClient, routes, serviceName);

    doReturn(null)
        .when(requestClient)
        .doAuthenticatedRequest(
            any(StitchAuthDocRequest.class));

    final Document expectedInfo = new Document("woo", "hoo");
    client.registerInternal(expectedInfo);

    final ArgumentCaptor<StitchAuthDocRequest> docArgument =
        ArgumentCaptor.forClass(StitchAuthDocRequest.class);
    verify(requestClient).doAuthenticatedRequest(docArgument.capture());

    assertEquals(docArgument.getValue().getMethod(), Method.PUT);
    assertEquals(docArgument.getValue().getPath(), routes.getRegistrationRoute(serviceName));
    assertEquals(docArgument.getValue().getDocument(), expectedInfo);

    // Should pass along errors
    doThrow(new IllegalArgumentException("whoops"))
        .when(requestClient).doAuthenticatedRequest(any(StitchAuthDocRequest.class));
    assertThrows(() -> {
      client.registerInternal(expectedInfo);
      return null;
    },IllegalArgumentException.class);
  }

  @Test
  public void testDeregister() {
    final String serviceName = "svc1";
    final StitchPushRoutes routes = new StitchPushRoutes("foo");
    final StitchAuthRequestClient requestClient = Mockito.mock(StitchAuthRequestClient.class);
    final CoreStitchPushClient client =
        new CoreStitchPushClientImpl(requestClient, routes, serviceName);

    doReturn(null)
        .when(requestClient)
        .doAuthenticatedRequest(
            any(StitchAuthRequest.class));

    client.deregisterInternal();

    final ArgumentCaptor<StitchAuthRequest> docArgument =
        ArgumentCaptor.forClass(StitchAuthRequest.class);
    verify(requestClient).doAuthenticatedRequest(docArgument.capture());

    assertEquals(docArgument.getValue().getMethod(), Method.DELETE);
    assertEquals(docArgument.getValue().getPath(), routes.getRegistrationRoute(serviceName));

    // Should pass along errors
    doThrow(new IllegalArgumentException("whoops"))
        .when(requestClient).doAuthenticatedRequest(any(StitchAuthRequest.class));
    assertThrows(() -> {
      client.deregisterInternal();
      return null;
    },IllegalArgumentException.class);
  }
}
