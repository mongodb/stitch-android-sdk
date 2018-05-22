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

package com.mongodb.stitch.server.core.auth.providers.userpassword.internal;

import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes;
import com.mongodb.stitch.core.auth.providers.userpassword.internal.CoreUserPasswordAuthProviderClient;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;
import com.mongodb.stitch.server.core.auth.providers.userpassword.UserPasswordAuthProviderClient;

/**
 * The implementation of the User/Password authentication provider client.
 */
public final class UserPasswordAuthProviderClientImpl extends CoreUserPasswordAuthProviderClient
    implements UserPasswordAuthProviderClient {

  /**
   * Constructs a new User/Password authentication provider client.
   * @param providerName the provider this client is for.
   * @param requestClient the request client used to make requests against Stitch.
   * @param routes the routes needed by the request client.
   */
  public UserPasswordAuthProviderClientImpl(
      final String providerName,
      final StitchRequestClient requestClient,
      final StitchAuthRoutes routes
  ) {
    super(providerName, requestClient, routes);
  }

  /**
   * Registers a new user with the given email and password.
   *
   * @param email the email to register with. This will be the username used during log in.
   * @param password the password to associated with the email. The password must be between
   *                 6 and 128 characters long.
   */
  public void registerWithEmail(final String email, final String password) {
    registerWithEmailInternal(email, password);
  }

  /**
   * Confirms a user with the given token and token id.
   *
   * @param token the confirmation token.
   * @param tokenId the id of the confirmation token.
   */
  public void confirmUser(final String token, final String tokenId) {
    confirmUserInternal(token, tokenId);
  }

  /**
   * Resend the confirmation for a user to the given email.
   *
   * @param email the email of the user.
   */
  public void resendConfirmationEmail(final String email) {
    resendConfirmationEmailInternal(email);
  }

  /**
   * Resets the password of a user with the given token, token id, and new password.
   *
   * @param token the reset password token.
   * @param tokenId the id of the reset password token.
   * @param password the new password for the user. The password must be between
   *                 6 and 128 characters long.
   */
  public void resetPassword(final String token, final String tokenId, final String password) {
    resetPasswordInternal(token, tokenId, password);
  }

  /**
   * Sends a user a password reset email for the given email.
   *
   * @param email the email of the user.
   */
  public void sendResetPasswordEmail(final String email) {
    sendResetPasswordEmailInternal(email);
  }
}
