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


package com.mongodb.stitch.core.auth.providers.userapikey;

import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient;
import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes;

import com.mongodb.stitch.core.internal.net.Method;
import com.mongodb.stitch.core.internal.net.Response;
import com.mongodb.stitch.core.internal.net.StitchAppRoutes;
import com.mongodb.stitch.core.internal.net.StitchAuthDocRequest;
import com.mongodb.stitch.core.internal.net.StitchAuthRequest;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.function.Function;

import javax.annotation.Nullable;

import static com.mongodb.stitch.core.testutils.Assert.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CoreUserApiKeyAuthProviderClientUnitTests {

  private static final String sampleFullApiKeyResponse =
          "{\"_id\": \"5aff1768b75e598e62f209e5\", " +
          "\"key\":\"blah\", " +
          "\"name\":\"api_key_name\", " +
          "\"disabled\": false }";

  private static final String samplePartialApiKeyResponse =
          "{\"_id\": \"5aff1768b75e598e62f209e5\", " +
          "\"name\":\"api_key_name\", " +
          "\"disabled\": false }";

  private void testClientCall(
          final Function<CoreUserApiKeyAuthProviderClient, Void> fun,
          final @Nullable Response desiredResponse,
          final StitchAuthRequest expectedRequest
  ) {
    final String clientAppId = "my_app-12345";

    final StitchAuthRequestClient requestClient = Mockito.mock(StitchAuthRequestClient.class);
    if(desiredResponse != null) {
      when(requestClient.doAuthenticatedRequest(any())).thenReturn(desiredResponse);
    }

    final StitchAuthRoutes routes = new StitchAppRoutes(clientAppId).getAuthRoutes();

    final CoreUserApiKeyAuthProviderClient client =
            new CoreUserApiKeyAuthProviderClient(requestClient, routes);

    fun.apply(client);
    verify(requestClient, times(1)).doAuthenticatedRequest(any());

    final ArgumentCaptor<StitchAuthRequest> requestArg =
            ArgumentCaptor.forClass(StitchAuthRequest.class);
    verify(requestClient).doAuthenticatedRequest(requestArg.capture());

    assertEquals(expectedRequest, requestArg.getValue());

    // Should pass along errors
    doThrow(new IllegalArgumentException("whoops")).when(requestClient).doAuthenticatedRequest(any());
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
            new Response(sampleFullApiKeyResponse),
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
            new Response(samplePartialApiKeyResponse),
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
            new Response(String.format("[%s, %s]",
                    samplePartialApiKeyResponse, samplePartialApiKeyResponse)
            ),
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
            .withPath(routes.getBaseAuthRoute() +
                    "/api_keys/" + keyToEnable.toHexString() + "/enable")
            .withRefreshToken()
            .withShouldRefreshOnFailure(false);

    testClientCall(
            (client) -> {
              client.enableApiKeyInternal(keyToEnable);
              return null;
            },
            null,
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
            .withPath(routes.getBaseAuthRoute() +
                    "/api_keys/" + keyToDisable.toHexString() + "/disable")
            .withRefreshToken()
            .withShouldRefreshOnFailure(false);

    testClientCall(
            (client) -> {
              client.disableApiKeyInternal(keyToDisable);
              return null;
            },
            null,
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
            null,
            expectedRequestBuilder.build()
    );
  }
}
