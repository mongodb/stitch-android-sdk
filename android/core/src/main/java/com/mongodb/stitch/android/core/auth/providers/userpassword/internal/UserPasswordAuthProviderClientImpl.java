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

package com.mongodb.stitch.android.core.auth.providers.userpassword.internal;

import android.support.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.auth.providers.userpassword.UserPasswordAuthProviderClient;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes;
import com.mongodb.stitch.core.auth.providers.userpassword.internal.CoreUserPasswordAuthProviderClient;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * The implementation of the User/Password authentication provider client.
 */
public final class UserPasswordAuthProviderClientImpl extends CoreUserPasswordAuthProviderClient
    implements UserPasswordAuthProviderClient {

  private final TaskDispatcher dispatcher;

  /**
   * Constructs a new User/Password authentication provider client.
   * @param providerName the provider this client is for.
   * @param requestClient the request client used to make requests against Stitch.
   * @param routes the routes needed by the request client.
   * @param dispatcher the task dispatcher for async requests.
   */
  public UserPasswordAuthProviderClientImpl(
      final String providerName,
      final StitchRequestClient requestClient,
      final StitchAuthRoutes routes,
      final TaskDispatcher dispatcher) {
    super(providerName, requestClient, routes);
    this.dispatcher = dispatcher;
  }

  /**
   * Registers a new user with the given email and password.
   *
   * @param email the email to register with. This will be the username used during log in.
   * @param password the password to associated with the email. The password must be between
   *                 6 and 128 characters long.
   * @return A {@link Task} that completes when registration completes/fails.
   */
  public Task<Void> registerWithEmail(@NonNull final String email, @NonNull final String password) {
    return dispatcher.dispatchTask(
        new Callable<Void>() {
          @Override
          public Void call() {
            registerWithEmailInternal(email, password);
            return null;
          }
        });
  }

  /**
   * Confirms a user with the given token and token id.
   *
   * @param token the confirmation token.
   * @param tokenId the id of the confirmation token.
   * @return A {@link Task} that completes when confirmation completes/fails.
   */
  public Task<Void> confirmUser(@NonNull final String token, @NonNull final String tokenId) {
    return dispatcher.dispatchTask(
        new Callable<Void>() {
          @Override
          public Void call() {
            confirmUserInternal(token, tokenId);
            return null;
          }
        });
  }

  /**
   * Resend the confirmation for a user to the given email.
   *
   * @param email the email of the user.
   * @return A {@link Task} that completes when the resend request completes/fails.
   */
  public Task<Void> resendConfirmationEmail(@NonNull final String email) {
    return dispatcher.dispatchTask(
        new Callable<Void>() {
          @Override
          public Void call() {
            resendConfirmationEmailInternal(email);
            return null;
          }
        });
  }

  /**
   * Resets the password of a user with the given token, token id, and new password.
   *
   * @param token the reset password token.
   * @param tokenId the id of the reset password token.
   * @param password the new password for the user. The password must be between
   *                 6 and 128 characters long.
   * @return A {@link Task} that completes when the password reset completes/fails.
   */
  public Task<Void> resetPassword(
      @NonNull final String token, @NonNull final String tokenId, @NonNull final String password) {
    return dispatcher.dispatchTask(
        new Callable<Void>() {
          @Override
          public Void call() {
            resetPasswordInternal(token, tokenId, password);
            return null;
          }
        });
  }

  /**
   * Sends a user a password reset email for the given email.
   *
   * @param email the email of the user.
   * @return A {@link Task} that completes when the reqest request completes/fails.
   */
  public Task<Void> sendResetPasswordEmail(@NonNull final String email) {
    return dispatcher.dispatchTask(
        new Callable<Void>() {
          @Override
          public Void call() {
            sendResetPasswordEmailInternal(email);
            return null;
          }
        });
  }

  @Override
  public Task<Void> callResetPasswordFunction(final String email,
                                              final String password,
                                              final List<?> args) {
    return dispatcher.dispatchTask(
        new Callable<Void>() {
          @Override
          public Void call() {
            callResetPasswordFunctionInternal(email, password, args);
            return null;
          }
        });
  }
}
