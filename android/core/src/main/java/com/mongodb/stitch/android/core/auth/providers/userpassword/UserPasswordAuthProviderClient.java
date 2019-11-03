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
import com.mongodb.stitch.android.core.auth.providers.userpassword.internal.UserPasswordAuthProviderClientImpl;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient;
import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes;
import com.mongodb.stitch.core.auth.providers.userpassword.UserPasswordAuthProvider;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;

import java.util.List;

/**
 * The User/Password authentication provider client used to register users.
 */
public interface UserPasswordAuthProviderClient {

  /**
   * Registers a new user with the given email and password.
   *
   * @param email the email to register with. This will be the username used during log in.
   * @param password the password to associated with the email. The password must be between
   *                 6 and 128 characters long.
   * @return A {@link Task} that completes when registration completes/fails.
   */
  Task<Void> registerWithEmail(@NonNull final String email, @NonNull final String password);

  /**
   * Confirms a user with the given token and token id.
   *
   * @param token the confirmation token.
   * @param tokenId the id of the confirmation token.
   * @return A {@link Task} that completes when confirmation completes/fails.
   */
  Task<Void> confirmUser(@NonNull final String token, @NonNull final String tokenId);

  /**
   * Resend the confirmation for a user to the given email.
   *
   * @param email the email of the user.
   * @return A {@link Task} that completes when the resend request completes/fails.
   */
  Task<Void> resendConfirmationEmail(@NonNull final String email);

  /**
   * Resets the password of a user with the given token, token id, and new password.
   *
   * @param token the reset password token.
   * @param tokenId the id of the reset password token.
   * @param password the new password for the user. The password must be between
   *                 6 and 128 characters long.
   * @return A {@link Task} that completes when the password reset completes/fails.
   */
  Task<Void> resetPassword(
      @NonNull final String token, @NonNull final String tokenId, @NonNull final String password);

  /**
   * Sends a user a password reset email for the given email.
   *
   * @param email the email of the user.
   * @return A {@link Task} that completes when the reqest request completes/fails.
   */
  Task<Void> sendResetPasswordEmail(@NonNull final String email);

  /**
   * Call a reset password function configured to the provider.
   * @param email the email of the user
   * @param password the new password to set
   * @param args arguments to be provided to the reset function
   * @return A {@link Task} that completes when the reqest request completes/fails.
   */
  Task<Void> callResetPasswordFunction(final String email,
                                       final String password,
                                       final List<?> args);

  AuthProviderClientFactory<UserPasswordAuthProviderClient> factory =
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
