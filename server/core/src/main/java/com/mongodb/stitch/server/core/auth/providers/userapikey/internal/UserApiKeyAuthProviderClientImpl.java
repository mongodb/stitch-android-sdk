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

package com.mongodb.stitch.server.core.auth.providers.userapikey.internal;

import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient;
import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes;
import com.mongodb.stitch.core.auth.providers.userapikey.CoreUserApiKeyAuthProviderClient;
import com.mongodb.stitch.core.auth.providers.userapikey.UserApiKeyAuthProvider;
import com.mongodb.stitch.core.auth.providers.userapikey.models.UserApiKey;
import com.mongodb.stitch.server.core.auth.providers.userapikey.UserApiKeyAuthProviderClient;

import org.bson.types.ObjectId;

import java.util.List;

public final class UserApiKeyAuthProviderClientImpl extends CoreUserApiKeyAuthProviderClient
    implements UserApiKeyAuthProviderClient {

  public UserApiKeyAuthProviderClientImpl(
          final StitchAuthRequestClient requestClient,
          final StitchAuthRoutes routes
          ) {
    super(requestClient, routes);
  }

  /**
   * Creates a user API key that can be used to authenticate as the current user.
   *
   * @param name The name of the API key to be created
   * @return The created API key.
   */
  @Override
  public UserApiKey createApiKey(String name) {
    return createApiKeyInternal(name);
  }

  /**
   * Fetches a user API key associated with the current user.
   *
   * @param id The id of the API key to fetch
   * @return The fetched API key.
   */
  @Override
  public UserApiKey fetchApiKey(ObjectId id) {
    return fetchApiKeyInternal(id);
  }

  /**
   * Fetches the user API keys associated with the current user.
   *
   * @return A list of the fetched API keys.
   */
  @Override
  public List<UserApiKey> fetchApiKeys() {
    return fetchApiKeysInternal();
  }

  /**
   * Deletes a user API key associated with the current user.
   *
   * @param id The id of the API key to delete.
   */
  @Override
  public void deleteApiKey(ObjectId id) {
    deleteApiKeyInternal(id);
  }

  /**
   * Enables a user API key associated with the current user.
   *
   * @param id The id of the API key to enable.
   */
  @Override
  public void enableApiKey(ObjectId id) {
    enableApiKeyInternal(id);
  }

  /**
   * Disables a user API key associated with the current user.
   *
   * @param id The id of the API key to disable.
   */
  @Override
  public void disableApiKey(ObjectId id) {
    disableApiKeyInternal(id);
  }
}
