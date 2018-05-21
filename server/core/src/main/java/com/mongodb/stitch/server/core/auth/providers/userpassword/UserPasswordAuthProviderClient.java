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

package com.mongodb.stitch.server.core.auth.providers.userpassword;

import com.mongodb.stitch.server.core.auth.providers.internal.AuthProviderClientFactory;
import com.mongodb.stitch.server.core.auth.providers.userpassword.internal.UserPasswordAuthProviderClientImpl;

public interface UserPasswordAuthProviderClient {
  void registerWithEmail(final String email, final String password);

  void confirmUser(final String token, final String tokenId);

  void resendConfirmationEmail(final String email);

  void resetPassword(final String token, final String tokenId, final String password);

  void sendResetPasswordEmail(final String email);

  AuthProviderClientFactory<UserPasswordAuthProviderClient> Factory =
          (authRequestClient, requestClient, routes) ->
                  new UserPasswordAuthProviderClientImpl(requestClient, routes);
}
