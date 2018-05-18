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

package com.mongodb.stitch.server.core.auth.providers.userapikey;

import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient;
import com.mongodb.stitch.core.auth.providers.userapikey.models.UserApiKey;
import com.mongodb.stitch.server.core.auth.providers.internal.AuthProviderClientFactory;
import com.mongodb.stitch.server.core.auth.providers.userapikey.internal.UserApiKeyAuthProviderClientImpl;

import org.bson.types.ObjectId;

import java.util.List;

public interface UserApiKeyAuthProviderClient {
  /**
   * Creates a user API key that can be used to authenticate as the current user.
   *
   * @param name The name of the API key to be created
   * @return The created API key.
   */
  UserApiKey createApiKey(final String name);

  /**
   * Fetches a user API key associated with the current user.
   *
   * @param id The id of the API key to fetch
   * @return The fetched API key.
   */
  UserApiKey fetchApiKey(final ObjectId id);

  /**
   * Fetches the user API keys associated with the current user.
   *
   * @return A list of the fetched API keys.
   */
  List<UserApiKey> fetchApiKeys();

  /**
   * Deletes a user API key associated with the current user.
   *
   * @param id The id of the API key to delete.
   */
  void deleteApiKey(final ObjectId id);

  /**
   * Enables a user API key associated with the current user.
   *
   * @param id The id of the API key to enable.
   */
  void enableApiKey(final ObjectId id);

  /**
   * Disables a user API key associated with the current user.
   *
   * @param id The id of the API key to disable.
   */
  void disableApiKey(final ObjectId id);

  AuthProviderClientFactory<UserApiKeyAuthProviderClient, StitchAuthRequestClient> Factory =
          UserApiKeyAuthProviderClientImpl::new;
}
