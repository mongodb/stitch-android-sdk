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


package com.mongodb.stitch.core.auth.providers.userapikey.internal;

import static com.mongodb.stitch.core.testutils.Assert.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient;
import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes;

import com.mongodb.stitch.core.internal.net.Method;
import com.mongodb.stitch.core.internal.net.StitchAppRoutes;
import com.mongodb.stitch.core.internal.net.StitchAuthDocRequest;
import com.mongodb.stitch.core.internal.net.StitchAuthRequest;

import java.util.function.Function;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class CoreUserApiKeyAuthProviderClientUnitTests {
  private void testClientCall(
          final Function<CoreUserApiKeyAuthProviderClient, Void> fun,
          final boolean ignoresResponse,
          final StitchAuthRequest expectedRequest
  ) {
    final String clientAppId = "my_app-12345";

    final StitchAuthRequestClient requestClient = Mockito.mock(StitchAuthRequestClient.class);

    final StitchAuthRoutes routes = new StitchAppRoutes(clientAppId).getAuthRoutes();

    final CoreUserApiKeyAuthProviderClient client =
            new CoreUserApiKeyAuthProviderClient(requestClient, routes);

    fun.apply(client);
    if (ignoresResponse) {
      verify(requestClient, times(1)).doAuthenticatedRequest(any());
    } else {
      verify(requestClient, times(1)).doAuthenticatedRequest(any(), any());
    }

    final ArgumentCaptor<StitchAuthRequest> requestArg =
            ArgumentCaptor.forClass(StitchAuthRequest.class);

    if (ignoresResponse) {
      verify(requestClient).doAuthenticatedRequest(requestArg.capture());
    } else {
      verify(requestClient).doAuthenticatedRequest(requestArg.capture(), any());
    }

    assertEquals(expectedRequest, requestArg.getValue());

    // Should pass along errors
    if (ignoresResponse) {
      doThrow(new IllegalArgumentException("whoops"))
              .when(requestClient).doAuthenticatedRequest(any());
    } else {
      doThrow(new IllegalArgumentException("whoops"))
              .when(requestClient).doAuthenticatedRequest(any(), any());
    }

    assertThrows(() -> {
      fun.apply(client);
      return null;
    }, IllegalArgumentException.class);
  }


  @Test
  public void testCreateApiKey() {
    final StitchAuthRoutes routes = new StitchAppRoutes("my_app-12345").getAuthRoutes();
    final String apiKeyName = "api_key_name";

    final StitchAuthDocRequest.Builder expectedRequestBuilder = new StitchAuthDocRequest.Builder();
    expectedRequestBuilder
            .withHeaders(null)
            .withMethod(Method.POST)
            .withPath(routes.getBaseAuthRoute() + "/api_keys")
            .withRefreshToken()
            .withShouldRefreshOnFailure(false)
            .withDocument(new Document("name", apiKeyName));

    testClientCall(
        (client) -> {
          client.createApiKeyInternal(apiKeyName);
          return null;
        },
        false,
        expectedRequestBuilder.build()
    );
  }

  @Test
  public void testFetchApiKey() {
    final StitchAuthRoutes routes = new StitchAppRoutes("my_app-12345").getAuthRoutes();
    final ObjectId keyToFetch = new ObjectId();

    final StitchAuthRequest.Builder expectedRequestBuilder = new StitchAuthRequest.Builder();
    expectedRequestBuilder
            .withHeaders(null)
            .withMethod(Method.GET)
            .withPath(routes.getBaseAuthRoute() + "/api_keys/" + keyToFetch.toHexString())
            .withRefreshToken()
            .withShouldRefreshOnFailure(false);

    testClientCall(
        (client) -> {
          client.fetchApiKeyInternal(keyToFetch);
          return null;
        },
        false,
        expectedRequestBuilder.build()
    );
  }

  @Test
  public void testFetchApiKeys() {
    final StitchAuthRoutes routes = new StitchAppRoutes("my_app-12345").getAuthRoutes();

    final StitchAuthRequest.Builder expectedRequestBuilder = new StitchAuthRequest.Builder();
    expectedRequestBuilder
            .withHeaders(null)
            .withMethod(Method.GET)
            .withPath(routes.getBaseAuthRoute() + "/api_keys")
            .withRefreshToken()
            .withShouldRefreshOnFailure(false);

    testClientCall(
        (client) -> {
          client.fetchApiKeysInternal();
          return null;
        },
        false,
        expectedRequestBuilder.build()
    );
  }

  @Test
  public void testEnableApiKey() {
    final StitchAuthRoutes routes = new StitchAppRoutes("my_app-12345").getAuthRoutes();
    final ObjectId keyToEnable = new ObjectId();

    final StitchAuthRequest.Builder expectedRequestBuilder = new StitchAuthRequest.Builder();
    expectedRequestBuilder
            .withHeaders(null)
            .withMethod(Method.PUT)
            .withPath(routes.getBaseAuthRoute()
                    + "/api_keys/" + keyToEnable.toHexString() + "/enable")
            .withRefreshToken()
            .withShouldRefreshOnFailure(false);

    testClientCall(
        (client) -> {
          client.enableApiKeyInternal(keyToEnable);
          return null;
        },
        true,
        expectedRequestBuilder.build()
    );
  }

  @Test
  public void testDisableApiKey() {
    final StitchAuthRoutes routes = new StitchAppRoutes("my_app-12345").getAuthRoutes();
    final ObjectId keyToDisable = new ObjectId();

    final StitchAuthRequest.Builder expectedRequestBuilder = new StitchAuthRequest.Builder();
    expectedRequestBuilder
            .withHeaders(null)
            .withMethod(Method.PUT)
            .withPath(routes.getBaseAuthRoute()
                    + "/api_keys/" + keyToDisable.toHexString() + "/disable")
            .withRefreshToken()
            .withShouldRefreshOnFailure(false);

    testClientCall(
        (client) -> {
          client.disableApiKeyInternal(keyToDisable);
          return null;
        },
        true,
        expectedRequestBuilder.build()
    );
  }

  @Test
  public void testDeleteApiKey() {
    final StitchAuthRoutes routes = new StitchAppRoutes("my_app-12345").getAuthRoutes();
    final ObjectId keyToDelete = new ObjectId();

    final StitchAuthRequest.Builder expectedRequestBuilder = new StitchAuthRequest.Builder();
    expectedRequestBuilder
            .withHeaders(null)
            .withMethod(Method.DELETE)
            .withPath(routes.getBaseAuthRoute() + "/api_keys/" + keyToDelete.toHexString())
            .withRefreshToken()
            .withShouldRefreshOnFailure(false);

    testClientCall(
        (client) -> {
          client.deleteApiKeyInternal(keyToDelete);
          return null;
        },
        true,
        expectedRequestBuilder.build()
    );
  }
}
