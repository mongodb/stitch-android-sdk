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

package com.mongodb.stitch.core.auth.providers.userpass;

import static com.mongodb.stitch.core.testutils.Assert.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes;
import com.mongodb.stitch.core.internal.net.Method;
import com.mongodb.stitch.core.internal.net.StitchAppRoutes;
import com.mongodb.stitch.core.internal.net.StitchDocRequest;
import com.mongodb.stitch.core.internal.net.StitchRequest;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;
import java.util.function.Function;

import org.bson.Document;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class CoreUserPasswordAuthProviderClientUnitTests {

  private void testClientCall(
      final Function<CoreUserPasswordAuthProviderClient, Void> fun,
      final StitchRequest expectedRequest
  ) {
    final String clientAppId = "my_app-12345";
    final String providerName = "userPassProvider";

    final StitchRequestClient requestClient = Mockito.mock(StitchRequestClient.class);
    final StitchAuthRoutes routes = new StitchAppRoutes(clientAppId).getAuthRoutes();
    final CoreUserPasswordAuthProviderClient client = new CoreUserPasswordAuthProviderClient(
        providerName,
        requestClient,
        routes
    );

    fun.apply(client);
    verify(requestClient, times(1)).doRequest(any());

    final ArgumentCaptor<StitchRequest> requestArg = ArgumentCaptor.forClass(StitchRequest.class);
    verify(requestClient).doRequest(requestArg.capture());

    assertEquals(expectedRequest, requestArg.getValue());

    // Should pass along errors
    doThrow(new IllegalArgumentException("whoops")).when(requestClient).doRequest(any());
    assertThrows(() -> {
      fun.apply(client);
      return null;
    }, IllegalArgumentException.class);
  }

  @Test
  public void testRegister() {
    final StitchAuthRoutes routes = new StitchAppRoutes("my_app-12345").getAuthRoutes();
    final String username = "username@10gen.com";
    final String password = "password";
    final StitchDocRequest.Builder expectedRequestBuilder = new StitchDocRequest.Builder();
    expectedRequestBuilder.withHeaders(null)
        .withMethod(Method.POST)
        .withPath(routes.getAuthProviderExtensionRoute("userPassProvider", "register"));
    final Document expectedDoc = new Document("email", username);
    expectedDoc.put("password", password);
    expectedRequestBuilder.withDocument(expectedDoc);

    testClientCall(
        (client) -> {
          client.registerWithEmailInternal(username, password);
          return null;
        },
        expectedRequestBuilder.build());
  }

  @Test
  public void testConfirmUser() {
    final StitchAuthRoutes routes = new StitchAppRoutes("my_app-12345").getAuthRoutes();
    final String token = "some";
    final String tokenId = "thing";
    final StitchDocRequest.Builder expectedRequestBuilder = new StitchDocRequest.Builder();
    expectedRequestBuilder.withHeaders(null)
        .withMethod(Method.POST)
        .withPath(routes.getAuthProviderExtensionRoute("userPassProvider", "confirm"));
    final Document expectedDoc = new Document("token", token);
    expectedDoc.put("tokenId", tokenId);
    expectedRequestBuilder.withDocument(expectedDoc);

    testClientCall(
        (client) -> {
          client.confirmUserInternal(token, tokenId);
          return null;
        },
        expectedRequestBuilder.build());
  }

  @Test
  public void testResendConfirmation() {
    final StitchAuthRoutes routes = new StitchAppRoutes("my_app-12345").getAuthRoutes();
    final String email = "username@10gen.com";
    final StitchDocRequest.Builder expectedRequestBuilder = new StitchDocRequest.Builder();
    expectedRequestBuilder.withHeaders(null)
        .withMethod(Method.POST)
        .withPath(routes.getAuthProviderExtensionRoute("userPassProvider", "confirm/send"));
    final Document expectedDoc = new Document("email", email);
    expectedRequestBuilder.withDocument(expectedDoc);

    testClientCall(
        (client) -> {
          client.resendConfirmationEmailInternal(email);
          return null;
        },
        expectedRequestBuilder.build());
  }
}
