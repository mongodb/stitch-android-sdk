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
import com.mongodb.stitch.core.auth.providers.userpass.CoreUserPasswordAuthProviderClient;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;
import com.mongodb.stitch.server.core.auth.providers.userpassword.UserPasswordAuthProviderClient;

public final class UserPasswordAuthProviderClientImpl extends CoreUserPasswordAuthProviderClient
    implements UserPasswordAuthProviderClient {

  public UserPasswordAuthProviderClientImpl(
      final StitchRequestClient requestClient, final StitchAuthRoutes routes) {
    super(CoreUserPasswordAuthProviderClient.DEFAULT_PROVIDER_NAME, requestClient, routes);
  }

  public void registerWithEmail(final String email, final String password) {
    registerWithEmailInternal(email, password);
  }

  public void confirmUser(final String token, final String tokenId) {
    confirmUserInternal(token, tokenId);
  }

  public void resendConfirmationEmail(final String email) {
    resendConfirmationEmailInternal(email);
  }

  public void resetPassword(final String token, final String tokenId, final String password) {
    resetPasswordInternal(token, tokenId, password);
  }

  public void sendResetPasswordEmail(final String email) {
    sendResetPasswordEmailInternal(email);
  }
}
