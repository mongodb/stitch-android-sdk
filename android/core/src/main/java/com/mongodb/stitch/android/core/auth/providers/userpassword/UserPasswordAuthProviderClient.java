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

package com.mongodb.stitch.android.core.auth.providers.userpassword;

import android.support.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.auth.providers.internal.AuthProviderClientFactory;
import com.mongodb.stitch.android.core.auth.providers.internal.NamedAuthProviderClientFactory;
import com.mongodb.stitch.android.core.auth.providers.userpassword.internal.UserPasswordAuthProviderClientImpl;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient;
import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes;
import com.mongodb.stitch.core.auth.providers.userpass.UserPasswordAuthProvider;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;

public interface UserPasswordAuthProviderClient {

  /**
   * Registers a new user with the given email and password.
   *
   * @return A {@link Task} that completes when registration completes/fails.
   */
  Task<Void> registerWithEmail(@NonNull final String email, @NonNull final String password);

  /**
   * Confirms a user with the given token and token id.
   *
   * @return A {@link Task} that completes when confirmation completes/fails.
   */
  Task<Void> confirmUser(@NonNull final String token, @NonNull final String tokenId);

  /**
   * Resend the confirmation for a user to the given email.
   *
   * @return A {@link Task} that completes when the resend request completes/fails.
   */
  Task<Void> resendConfirmationEmail(@NonNull final String email);

  /**
   * Reset the password of a user with the given token, token id, and new password.
   *
   * @return A {@link Task} that completes when the password reset completes/fails.
   */
  Task<Void> resetPassword(
      @NonNull final String token, @NonNull final String tokenId, @NonNull final String password);

  /**
   * Sends a user a password reset email for the given email.
   *
   * @return A {@link Task} that completes when the reqest request completes/fails.
   */
  Task<Void> sendResetPasswordEmail(@NonNull final String email);

  AuthProviderClientFactory<UserPasswordAuthProviderClient> Factory =
      new AuthProviderClientFactory<UserPasswordAuthProviderClient>() {
        @Override
        public UserPasswordAuthProviderClient getClient(
            final StitchAuthRequestClient authRequestClient,
            final StitchRequestClient requestClient,
            final StitchAuthRoutes routes,
            final TaskDispatcher dispatcher
        ) {
          return new UserPasswordAuthProviderClientImpl(
              UserPasswordAuthProvider.DEFAULT_NAME,
              requestClient,
              routes,
              dispatcher);
        }
      };
}
