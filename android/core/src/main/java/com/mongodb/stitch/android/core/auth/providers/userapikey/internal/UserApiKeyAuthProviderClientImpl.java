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

package com.mongodb.stitch.android.core.auth.providers.userapikey.internal;

import android.support.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.auth.providers.userapikey.UserApiKeyAuthProviderClient;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient;
import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes;
import com.mongodb.stitch.core.auth.providers.userapikey.CoreUserApiKeyAuthProviderClient;
import com.mongodb.stitch.core.auth.providers.userapikey.models.UserApiKey;

import org.bson.types.ObjectId;

import java.util.List;
import java.util.concurrent.Callable;

public final class UserApiKeyAuthProviderClientImpl extends CoreUserApiKeyAuthProviderClient
    implements UserApiKeyAuthProviderClient {

  private final TaskDispatcher dispatcher;

  public UserApiKeyAuthProviderClientImpl(
          final StitchAuthRequestClient requestClient,
          final StitchAuthRoutes routes,
          final TaskDispatcher dispatcher) {
    super(requestClient, routes);
    this.dispatcher = dispatcher;
  }

  /**
   * Creates a user API key that can be used to authenticate as the current user.
   *
   * @param name The name of the API key to be created
   * @return A {@link Task} that contains the created API key.
   */
  @Override
  public Task<UserApiKey> createApiKey(@NonNull final String name) {
    return dispatcher.dispatchTask(new Callable<UserApiKey>() {
      @Override
      public UserApiKey call() {
        return createApiKeyInternal(name);
      }
    });
  }

  /**
   * Fetches a user API key associated with the current user.
   *
   * @param id The id of the API key to fetch.
   * @return A {@link Task} that contains the fetched API key.
   */
  @Override
  public Task<UserApiKey> fetchApiKey(@NonNull final ObjectId id) {
    return dispatcher.dispatchTask(new Callable<UserApiKey>() {
      @Override
      public UserApiKey call() {
        return fetchApiKeyInternal(id);
      }
    });
  }

  /**
   * Fetches the user API keys associated with the current user.
   *
   * @return A {@link Task} that contains the list of the fetched API keys.
   */
  @Override
  public Task<List<UserApiKey>> fetchApiKeys() {
    return dispatcher.dispatchTask(new Callable<List<UserApiKey>>() {
      @Override
      public List<UserApiKey> call() {
        return fetchApiKeysInternal();
      }
    });
  }

  /**
   * Deletes a user API key associated with the current user.
   *
   * @param id The id of the API key to delete.
   * @return A {@link Task} that completes when the API key is deleted.
   */
  @Override
  public Task<Void> deleteApiKey(@NonNull final ObjectId id) {
    return dispatcher.dispatchTask(new Callable<Void>() {
      @Override
      public Void call() {
        deleteApiKeyInternal(id);
        return null;
      }
    });
  }

  /**
   * Enables a user API key associated with the current user.
   *
   * @param id The id of the API key to enable.
   * @return A {@link Task} that completes when the API key is enabled.
   */
  @Override
  public Task<Void> enableApiKey(@NonNull final ObjectId id) {
    return dispatcher.dispatchTask(new Callable<Void>() {
      @Override
      public Void call() {
        enableApiKeyInternal(id);
        return null;
      }
    });
  }

  /**
   * Disables a user API key associated with the current user.
   *
   * @param id The id of the API key to disable.
   * @return A {@link Task} that completes when the API key is disabled.
   */
  @Override
  public Task<Void> disableApiKey(@NonNull final ObjectId id) {
    return dispatcher.dispatchTask(new Callable<Void>() {
      @Override
      public Void call() {
        disableApiKeyInternal(id);
        return null;
      }
    });
  }
}
