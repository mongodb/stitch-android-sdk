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
import com.mongodb.stitch.core.auth.providers.CoreAuthProviderClient;
import com.mongodb.stitch.core.auth.providers.userapikey.models.UserApiKey;
import com.mongodb.stitch.core.internal.net.Method;
import com.mongodb.stitch.core.internal.net.StitchAuthDocRequest;
import com.mongodb.stitch.core.internal.net.StitchAuthRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bson.BsonReader;
import org.bson.BsonType;
import org.bson.Document;
import org.bson.codecs.Decoder;
import org.bson.codecs.DecoderContext;
import org.bson.types.ObjectId;

public class CoreUserApiKeyAuthProviderClient
        extends CoreAuthProviderClient<StitchAuthRequestClient> {
  private final Routes routes;

  protected CoreUserApiKeyAuthProviderClient(final StitchAuthRequestClient authRequestClient,
                                             final StitchAuthRoutes authRoutes) {
    super(UserApiKeyAuthProvider.DEFAULT_NAME, authRequestClient,
            authRoutes.getBaseAuthRoute() + "/api_keys");
    this.routes = new Routes(getBaseRoute());
  }

  /**
   * Creates a user API key that can be used to authenticate as the current user.
   *
   * @param name The name of the API key to be created
   */
  protected UserApiKey createApiKeyInternal(final String name) {
    final StitchAuthDocRequest.Builder reqBuilder = new StitchAuthDocRequest.Builder();
    reqBuilder
            .withMethod(Method.POST)
            .withPath(this.getBaseRoute())
            .withDocument(new Document(Routes.ApiKeyFields.NAME, name))
            .withRefreshToken();
    return getRequestClient().doAuthenticatedRequest(
            reqBuilder.build(),
            new UserApiKey.UserApiKeyDecoder()
    );
  }

  /**
   * Fetches a user API key associated with the current user.
   *
   * @param id The id of the API key to be fetched.
   */
  protected UserApiKey fetchApiKeyInternal(final ObjectId id) {
    final StitchAuthRequest.Builder reqBuilder = new StitchAuthRequest.Builder();
    reqBuilder
            .withMethod(Method.GET)
            .withPath(routes.getApiKeyRouteForId(id.toHexString()))
            .withRefreshToken();
    return getRequestClient().doAuthenticatedRequest(
            reqBuilder.build(),
            new UserApiKey.UserApiKeyDecoder()
    );
  }

  /**
   * Fetches the user API keys associated with the current user.
   */
  protected List<UserApiKey> fetchApiKeysInternal() {
    final StitchAuthRequest.Builder reqBuilder = new StitchAuthRequest.Builder();
    reqBuilder
            .withMethod(Method.GET)
            .withPath(this.getBaseRoute())
            .withRefreshToken();
    return (List<UserApiKey>) getRequestClient().doAuthenticatedRequest(
            reqBuilder.build(),
            new CollectionDecoder<>(new UserApiKey.UserApiKeyDecoder()));
  }

  /**
   * Deletes a user API key associated with the current user.
   *
   * @param id The id of the API key to delete.
   */
  protected void deleteApiKeyInternal(final ObjectId id) {
    final StitchAuthRequest.Builder reqBuilder = new StitchAuthRequest.Builder();
    reqBuilder
            .withMethod(Method.DELETE)
            .withPath(routes.getApiKeyRouteForId(id.toHexString()))
            .withRefreshToken();
    getRequestClient().doAuthenticatedRequest(reqBuilder.build());
  }

  /**
   * Enables a user API key associated with the current user.
   *
   * @param id The id of the API key to enable.
   */
  protected void enableApiKeyInternal(final ObjectId id) {
    final StitchAuthRequest.Builder reqBuilder = new StitchAuthRequest.Builder();
    reqBuilder
            .withMethod(Method.PUT)
            .withPath(routes.getApiKeyEnableRouteForId(id.toHexString()))
            .withRefreshToken();
    getRequestClient().doAuthenticatedRequest(reqBuilder.build());
  }

  /**
   * Disables a user API key associated with the current user.
   *
   * @param id The id of the API key to disable.
   */
  protected void disableApiKeyInternal(final ObjectId id) {
    final StitchAuthRequest.Builder reqBuilder = new StitchAuthRequest.Builder();
    reqBuilder
            .withMethod(Method.PUT)
            .withPath(routes.getApiKeyDisableRouteForId(id.toHexString()))
            .withRefreshToken();
    getRequestClient().doAuthenticatedRequest(reqBuilder.build());
  }


  private static final class Routes {
    private final String baseRoute;

    Routes(final String baseRoute) {
      this.baseRoute = baseRoute;
    }

    private String getApiKeyRouteForId(final String id) {
      return String.format("%s/%s", baseRoute, id);
    }

    private String getApiKeyEnableRouteForId(final String id) {
      return getApiKeyRouteForId(id) + "/enable";
    }

    private String getApiKeyDisableRouteForId(final String id) {
      return getApiKeyRouteForId(id) + "/disable";
    }

    private static class ApiKeyFields {
      static final String NAME = "name";
    }
  }

  // TODO: Delete when bringing in from remote MongoDB PR
  private class CollectionDecoder<TResult> implements Decoder<Collection<TResult>> {
    private final Decoder<TResult> decoder;

    public CollectionDecoder(final Decoder<TResult> decoder) {
      this.decoder = decoder;
    }

    @Override
    public Collection<TResult> decode(final BsonReader reader, final DecoderContext decoderContext) {
      final Collection<TResult> docs = new ArrayList<>();
      reader.readStartArray();
      while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
        final TResult doc = decoder.decode(reader, decoderContext);
        docs.add(doc);
      }
      reader.readEndArray();
      return docs;
    }
  }
}
