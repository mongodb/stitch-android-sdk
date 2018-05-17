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

package com.mongodb.stitch.android.core.auth;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.auth.providers.internal.AuthProviderClientFactory;
import com.mongodb.stitch.android.core.auth.providers.internal.NamedAuthProviderClientFactory;
import com.mongodb.stitch.core.auth.StitchCredential;
import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;

import java.io.Closeable;

public interface StitchAuth extends Closeable {
  /**
   * Gets an authenticated client for the given authentication provider. Most authentication
   * providers will allow creation of a client without a name of the provider.
   *
   * @param provider The provider that will create a client for the authentication provider.
   * @param <ClientT> The type of client to be returned by the provider.
   * @return A client to interact with the authentication provider.
   */
  <ClientT> ClientT getAuthenticatedProviderClient(
          final AuthProviderClientFactory<ClientT, StitchAuthRequestClient> provider);

  /**
   * Gets a client for the given authentication provider. Most authentication providers will allow
   * creation of a client without a name of the provider.
   *
   * @param provider The provider that will create a client for the authentication provider.
   * @param <ClientT> The type of client to be returned by the provider.
   * @return A client to interact with the authentication provider.
   */
  <ClientT> ClientT getProviderClient(
          final AuthProviderClientFactory<ClientT, StitchRequestClient> provider);

  /**
   * Gets a client for the given named authentication provider.
   *
   * @param provider The provider that will create a client for the authentication provider.
   * @param providerName The name of the authentication provider.
   * @param <T> The type of client to be returned by the provider.
   * @return A client to interact with the authentication provider.
   */
  <T> T getProviderClient(
      final NamedAuthProviderClientFactory<T> provider, final String providerName);

  /**
   * Logs a user in with the given credentials associated with an authentication provider created
   * from {@link StitchAuth#getProviderClient(AuthProviderClientFactory)} or {@link
   * StitchAuth#getProviderClient(NamedAuthProviderClientFactory, String)}.
   *
   * @param credential The credentials of the user to log in.
   * @return A {@link Task} containing user associated with the credentials if log in is successful.
   */
  Task<StitchUser> loginWithCredential(final StitchCredential credential);

  /** Logs out the currently logged in user. */
  Task<Void> logout();

  /** Returns whether or not there's a currently logged in user. */
  boolean isLoggedIn();

  /** Returns the currently logged in user; may be null. */
  StitchUser getUser();

  /**
   * Adds a listener for any important auth event.
   *
   * @see StitchAuthListener
   */
  void addAuthListener(final StitchAuthListener listener);

  /**
   * Removes a listener.
   *
   * @see StitchAuthListener
   */
  void removeAuthListener(final StitchAuthListener listener);
}
