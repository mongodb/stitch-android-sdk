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

import android.support.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.auth.providers.internal.AuthProviderClientFactory;
import com.mongodb.stitch.android.core.auth.providers.internal.NamedAuthProviderClientFactory;
import com.mongodb.stitch.core.auth.StitchCredential;
import java.io.Closeable;
import java.util.List;

/**
 * StitchAuth manages authentication for any Stitch based client.
 * <p>
 * You can access the StitchAuth instance of the {@link com.mongodb.stitch.android.core.StitchAppClient} with 
 * {@link com.mongodb.stitch.android.core.StitchAppClient#getAuth()}.
 * </p><p>
 * This class handles login with various <a href="https://docs.mongodb.com/stitch/authentication/providers/" target=".">
 * authentication providers</a> using {@link StitchCredential}s with {@link loginWithCredential}.
 * </p><p>
 * Once logged in, you can retrieve the active {@link StitchUser} with {@link getUser},
 * switch the active user with {@link switchToUserWithId}, {@link logout}, and remove users
 * with {@link removeUserWithId}.
 * </p>
 * <h3>Working with Multiple User Accounts</h3>
 * <p>
 * The Stitch Client SDKs allow multiple users to log in at the same time. When multiple users
 * are logged in to an app, one of them is the <i>active user</i> for Stitch requests. You can
 * switch the active user to be any other logged in user at any time. This allows users to 
 * quickly switch between logged in accounts on a device.
 * </p><p>
 * When a user is logged out, they remain on the device and are returned in the {@link listUsers} list.
 * To log in again, another {@link loginWithCredential} call needs to be made. To remove a user from
 * the device, use {@link removeUserWithId}.
 * </p><p>
 * The following diagram explains the state of user accounts in your app when different events occur:
 * </p>
 * <img src="https://docs.mongodb.com/stitch/_images/multi-user.png" alt="Stitch User state diagram" >
 * 
 * @see com.mongodb.stitch.android.core.StitchAppClient
 * @see StitchUser
 * @see StitchCredential 
 */
public interface StitchAuth extends Closeable {
  /**
   * Gets a client for the given authentication provider. Most authentication providers will allow
   * creation of a client without a name of the provider.
   *
   * @param factory The factory that will create a client for the authentication provider.
   * @param <ClientT> The type of client to be returned by the factory.
   * @return A client to interact with the authentication provider.
   */
  <ClientT> ClientT getProviderClient(
          final AuthProviderClientFactory<ClientT> factory);

  /**
   * Gets a client for the given named authentication provider.
   *
   * @param factory the factory that will create a client for the authentication provider.
   * @param providerName the name of the authentication provider.
   * @param <T> the type of client to be returned by the factory.
   * @return a client to interact with the authentication provider.
   */
  <T> T getProviderClient(
      final NamedAuthProviderClientFactory<T> factory, final String providerName);

  /**
   * Logs in as a user with the given credentials associated with an authentication provider.
   * <p>
   * The user who logs in becomes the active user. Other Stitch functionality acts on behalf
   * of the active user. If there was already an active user, that user becomes inactive but
   * still logged in.
   * </p>
   * @see logout
   * @see logoutUserWithId
   * @see switchToUserWithId
   * @see removeUserWithId
   *
   * @param credential the credentials of the user to log in.
   * @return a {@link Task} containing user associated with the credentials if log in is successful.
   */
  Task<StitchUser> loginWithCredential(final StitchCredential credential);

  /**
   * Logs out the active user.
   * <p>
   * To log out an inactive user, use {@link logoutUserWithId}.
   * </p><p>
   * Except for anonymous users, logged out users remain on
   * the device and will be listed in the result of {@link listUsers}.
   * To log in again, {@link loginWithCredential} must be used.
   * To log out and remove the active user, use {@link removeUser}.
   * </p><p>
   * Anonymous users are deleted immediately after logging out.
   * </p>
   * @return a {@link Task} completing when logged out.
   */
  Task<Void> logout();

  /**
   * Logs out the user with the provided id.
   * Throws an exception if the user was not found.
   * <p>
   * Except for anonymous users, logged out users remain on
   * the device and will be listed in the result of {@link listUsers}.
   * To log in again, {@link loginWithCredential} must be used.
   * To remove a user from the list, use {@link removeUserWithId}.
   * </p><p>
   * Anonymous users are deleted immediately after logging out.
   * </p>
   * @param userId the id of the user to log out.
   * @return a {@link Task} completing when logged out.
   */
  Task<Void> logoutUserWithId(final String userId);

  /**
   * Logs out and removes the active user.
   * <p>
   * To remove an inactive user, see {@link removeUserWithId}.
   * </p><p>
   * Removing a user means removing it from this device,
   * i.e. removing it from the list of users returned by
   * {@link listUsers}.
   * </p>
   * @return a {@link Task} completing when logged out.
   */
  Task<Void> removeUser();

  /**
   * Logs out and removes the user with the provided id.
   * Throws an exception if the user was not found.
   * @param userId the id of the user to remove.
   * @return a {@link Task} completing when logged out.
   */
  Task<Void> removeUserWithId(final String userId);

  /**
   * Returns whether there's an active user.
   * <p>
   * Note: even if there are other users who are logged in,
   * this will return false if there is no <i>active</i> user.
   * A user becomes the active user upon {@link loginWithCredential}.
   * See {@link listUsers}.
   * </p>
   *
   * @return whether there's a currently active user.
   */
  boolean isLoggedIn();

  /**
   * Returns the active user or null if no user is active.
   * <p>
   * Whenever a user logs in, they become the active user. If there was already
   * another user who was active, that user becomes inactive but still logged in.
   * User-based Stitch functionality, such as reading from a database,
   * is performed as the active user.
   * </p>
   * @return the currently logged in, active user or null if no user is active.
   */
  @Nullable
  StitchUser getUser();

  /**
   * Adds a listener for any important auth event.
   *
   * @param listener the listener to add.
   * @see StitchAuthListener
   */
  void addAuthListener(final StitchAuthListener listener);

  /**
   * Removes a listener.
   *
   * @param listener the listener to remove.
   */
  void removeAuthListener(final StitchAuthListener listener);

  /**
   * Returns a list of all logged in users.
   *
   * @return the list of currently logged in users
   */
  List<StitchUser> listUsers();

  /**
   * Switches the active user to the user with the provided id.
   * Throws an exception if the user was not found.
   * @param userId the id of the user to switch to
   * @return the user that was switched to
   * @throws IllegalArgumentException throws if user id not found
   */
  StitchUser switchToUserWithId(final String userId) throws IllegalArgumentException;
}
