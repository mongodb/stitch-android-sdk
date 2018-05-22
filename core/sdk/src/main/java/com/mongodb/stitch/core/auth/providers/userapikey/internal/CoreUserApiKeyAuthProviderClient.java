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

import static com.mongodb.stitch.core.internal.common.Assertions.keyPresent;

import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient;
import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes;
import com.mongodb.stitch.core.auth.providers.internal.CoreAuthProviderClient;
import com.mongodb.stitch.core.auth.providers.userapikey.UserApiKeyAuthProvider;
import com.mongodb.stitch.core.auth.providers.userapikey.models.UserApiKey;
import com.mongodb.stitch.core.internal.common.CollectionDecoder;
import com.mongodb.stitch.core.internal.net.Method;
import com.mongodb.stitch.core.internal.net.StitchAuthDocRequest;
import com.mongodb.stitch.core.internal.net.StitchAuthRequest;
import java.util.List;
import org.bson.BsonReader;
import org.bson.Document;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodec;
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
   * @return the created API key.
   */
  protected UserApiKey createApiKeyInternal(final String name) {
    final StitchAuthDocRequest.Builder reqBuilder = new StitchAuthDocRequest.Builder();
    reqBuilder
            .withMethod(Method.POST)
            .withPath(this.getBaseRoute())
            .withDocument(new Document(ApiKeyFields.NAME, name))
            .withRefreshToken();
    return getRequestClient().doAuthenticatedRequest(
            reqBuilder.build(),
            new UserApiKeyDecoder()
    );
  }

  /**
   * Fetches a user API key associated with the current user.
   *
   * @param id The id of the API key to be fetched.
   * @return the API key.
   */
  protected UserApiKey fetchApiKeyInternal(final ObjectId id) {
    final StitchAuthRequest.Builder reqBuilder = new StitchAuthRequest.Builder();
    reqBuilder
            .withMethod(Method.GET)
            .withPath(routes.getApiKeyRouteForId(id.toHexString()))
            .withRefreshToken();
    return getRequestClient().doAuthenticatedRequest(
            reqBuilder.build(),
            new UserApiKeyDecoder()
    );
  }

  /**
   * Fetches the user API keys associated with the current user.
   *
   * @return the user API keys associated with the current user.
   */
  protected List<UserApiKey> fetchApiKeysInternal() {
    final StitchAuthRequest.Builder reqBuilder = new StitchAuthRequest.Builder();
    reqBuilder
            .withMethod(Method.GET)
            .withPath(this.getBaseRoute())
            .withRefreshToken();
    return (List<UserApiKey>) getRequestClient().doAuthenticatedRequest(
            reqBuilder.build(),
            new CollectionDecoder<>(new UserApiKeyDecoder()));
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
  }

  public static class ApiKeyFields {
    public static final String ID = "_id";
    public static final String KEY = "key";
    public static final String NAME = "name";
    public static final String DISABLED = "disabled";
  }

  private static final class UserApiKeyDecoder implements org.bson.codecs.Decoder<UserApiKey> {
    /**
     * Decodes a BSON value from the given reader into an instance of the type parameter {@code T}.
     *
     * @param reader the BSON reader
     * @param decoderContext the decoder context
     * @return an instance of the type parameter {@code T}.
     */
    @Override
    public UserApiKey decode(final BsonReader reader, final DecoderContext decoderContext) {
      final Document document = (new DocumentCodec()).decode(reader, decoderContext);
      keyPresent(ApiKeyFields.ID, document);
      keyPresent(ApiKeyFields.NAME, document);
      keyPresent(ApiKeyFields.DISABLED, document);
      return new UserApiKey(
          document.getString(ApiKeyFields.ID),
          document.getString(ApiKeyFields.KEY),
          document.getString(ApiKeyFields.NAME),
          document.getBoolean(ApiKeyFields.DISABLED)
      );
    }
  }
}
