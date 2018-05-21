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

package com.mongodb.stitch.android.core.auth.providers.userapikey;

import android.support.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.auth.providers.internal.AuthProviderClientFactory;
import com.mongodb.stitch.android.core.auth.providers.userapikey.internal.UserApiKeyAuthProviderClientImpl;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient;
import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes;
import com.mongodb.stitch.core.auth.providers.userapikey.models.UserApiKey;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;

import java.util.List;

import org.bson.types.ObjectId;

public interface UserApiKeyAuthProviderClient {
  /**
   * Creates a user API key that can be used to authenticate as the current user.
   *
   * @param name The name of the API key to be created
   * @return A {@link Task} that contains the created API key.
   */
  Task<UserApiKey> createApiKey(@NonNull final String name);

  /**
   * Fetches a user API key associated with the current user.
   *
   * @param id The id of the API key to fetch
   * @return A {@link Task} that contains the fetched API key.
   */
  Task<UserApiKey> fetchApiKey(@NonNull final ObjectId id);

  /**
   * Fetches the user API keys associated with the current user.
   *
   * @return A {@link Task} that contains the list of the fetched API keys.
   */
  Task<List<UserApiKey>> fetchApiKeys();

  /**
   * Deletes a user API key associated with the current user.
   *
   * @param id The id of the API key to delete.
   * @return A {@link Task} that completes when the API key is deleted.
   */
  Task<Void> deleteApiKey(@NonNull final ObjectId id);

  /**
   * Enables a user API key associated with the current user.
   *
   * @param id The id of the API key to enable.
   * @return A {@link Task} that completes when the API key is enabled.
   */
  Task<Void> enableApiKey(@NonNull final ObjectId id);

  /**
   * Disables a user API key associated with the current user.
   *
   * @param id The id of the API key to disable.
   * @return A {@link Task} that completes when the API key is disabled.
   */
  Task<Void> disableApiKey(@NonNull final ObjectId id);

  AuthProviderClientFactory<UserApiKeyAuthProviderClient> Factory =
      new AuthProviderClientFactory<UserApiKeyAuthProviderClient>() {
        @Override
        public UserApiKeyAuthProviderClient getClient(
                final StitchAuthRequestClient authRequestClient,
                final StitchRequestClient requestClient,
                final StitchAuthRoutes routes,
                final TaskDispatcher dispatcher
        ) {
          return new UserApiKeyAuthProviderClientImpl(authRequestClient, routes, dispatcher);
        }
      };
}
