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

package com.mongodb.stitch.server.core.auth;

import com.mongodb.stitch.core.auth.StitchCredential;
import com.mongodb.stitch.server.core.auth.providers.internal.AuthProviderClientFactory;
import com.mongodb.stitch.server.core.auth.providers.internal.NamedAuthProviderClientFactory;

public interface StitchAuth {
  /**
   * Gets a client for the given authentication provider. Most authentication providers will allow
   * creation of a client without a name of the provider.
   *
   * @param provider The provider that will create a client for the authentication provider.
   * @param <ClientT> The type of client to be returned by the provider.
   * @return A client to interact with the authentication provider.
   */
  <ClientT> ClientT getProviderClient(
          final AuthProviderClientFactory<ClientT> provider);

  /**
   * Gets a client for the given named authentication provider.
   *
   * @param provider the provider that will create a client for the authentication provider.
   * @param providerName the name of the authentication provider.
   * @param <T> the type of client to be returned by the provider.
   * @return A client to interact with the authentication provider.
   */
  <T> T getProviderClient(
      final NamedAuthProviderClientFactory<T> provider, final String providerName);

  /**
   * Logs a user in with the given credentials associated with an authentication provider created
   * from {@link StitchAuth#getProviderClient(AuthProviderClientFactory)} or {@link
   * StitchAuth#getProviderClient(NamedAuthProviderClientFactory, String)}.
   *
   * @param credential the credentials of the user to log in.
   * @return the user associated with the credentials if log in is successful.
   */
  StitchUser loginWithCredential(final StitchCredential credential);

  /** Logs out the currently logged in user. */
  void logout();

  /** Returns whether or not there's a currently logged in user. */
  boolean isLoggedIn();

  /** Returns the currently logged in user; may be null. */
  StitchUser getUser();
}
