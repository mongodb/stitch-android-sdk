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

import javax.annotation.Nullable;

/**
 * StitchAuthListener listens to a {@link StitchAuth} instance for authentication events
 * as they happen in a {@link com.mongodb.stitch.android.core.StitchAppClient}.
 * <p>
 * The listener can be added with {@link StitchAuth#addAuthListener}.
 * </p>
 * @see StitchAuth
 * @see com.mongodb.stitch.android.core.StitchAppClient
 */
public interface StitchAuthListener {

  /**
   * onAuthEvent is called any time a notable event regarding authentication happens.
   * Some of these events are:
   * <ul>
   * <li>When a user logs in.</li>
   * <li>When a user logs out.</li>
   * <li>When a user is linked to another identity, i.e. with
   * {@link StitchUser#linkWithCredential}.</li>
   * <li>When a listener is registered.
   * This is to handle the case where during registration an event happens
   * that the registerer would otherwise miss out on.</li>
   * <li>When switching active users, e.g. with {@link StitchAuth#switchToUserWithId}
   * or when another user logs in.</li>
   * </ul>
   *
   * @param auth the instance of {@link StitchAuth} where the event happened. It should be used to
   *             infer the current state of authentication.
   */
  @Deprecated
  default void onAuthEvent(final StitchAuth auth) {

  }

  /**
   * Called whenever a user is added to the device for the first time.
   * 
   * <p>If this is as part of a login, this method will be called before
   * {@link #onUserLoggedIn}, and {@link #onActiveUserChanged} are called.
   * </p>
   *
   * @see StitchAuth#listUsers
   * 
   * @param auth      The instance of {@link StitchAuth} where the user was added.
   *                  It can be used to infer the current state of authentication.
   * @param addedUser The user that was added to the device.
   */
  default void onUserAdded(final StitchAuth auth, final StitchUser addedUser) {
  }

  /**
   * Called whenever a user is logged in. This will be called before
   * {@link #onActiveUserChanged} is called.
   * <p>
   * Note: if an anonymous user was already logged in on the device, and you
   * log in with an {@link com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential},
   * this method will not be called, as the underlying {@link StitchAuth} will reuse the anonymous
   * user's existing session, and will thus only trigger {@link #onActiveUserChanged}.
   * </p>
   *
   * @param auth         The instance of {@link StitchAuth} where the user was logged in.
   *                     It can be used to infer the current state of authentication.
   * @param loggedInUser The user that was logged in.
   */
  default void onUserLoggedIn(final StitchAuth auth,
                              final StitchUser loggedInUser) {
  }

  /**
   * Called whenever a user is linked to a new identity, as in
   * {@link StitchUser#linkWithCredential}.
   *
   * @param auth       The instance of {@link StitchAuth} where the user was linked.
   *                   It can be used to infer the current state of authentication.
   * @param linkedUser The user that was linked to a new identity.
   */
  default void onUserLinked(final StitchAuth auth, final StitchUser linkedUser) {

  }

  /**
   * Called whenever a user is logged out.
   * <p>
   * The user logged out is not necessarily the active user. If the user logged out was
   * the active user, then {@link #onActiveUserChanged} will be called after this method.
   * </p><p>
   * If the user was an anonymous user, that user will also be removed and
   * {@link #onUserRemoved} will also be called.
   * </p>
   *
   * @param auth          The instance of {@link StitchAuth} where the user was logged out.
   *                      It can be used to infer the current state of authentication.
   * @param loggedOutUser The user that was logged out.
   */
  default void onUserLoggedOut(final StitchAuth auth, final StitchUser loggedOutUser) {

  }

  /**
   * Called whenever the active user changes.
   *
   * <p>This may be due to a call to:
   * <ul>
   * <li>{@link StitchAuth#loginWithCredential}</li>
   * <li>{@link StitchAuth#switchToUserWithId}</li>
   * <li>{@link StitchAuth#logout}</li>
   * <li>{@link StitchAuth#logoutUserWithId}</li>
   * <li>{@link StitchAuth#removeUser}</li>
   * <li>{@link StitchAuth#removeUserWithId}, or</li>
   * <li>Another method that issues a request, if the user's session is invalidated and
   * they are forced to log out.</li>
   * </ul>
   *
   * @param auth               The instance of {@link StitchAuth} where the active user changed.
   *                           It can be used to infer the current state of authentication.
   * @param currentActiveUser  The active user after the change.
   * @param previousActiveUser The active user before the change.
   */
  default void onActiveUserChanged(final StitchAuth auth,
                                   @Nullable final StitchUser currentActiveUser,
                                   @Nullable final StitchUser previousActiveUser) {

  }

  /**
   * Called whenever a user is removed from the list of users on the device.
   *
   * @see StitchAuth#listUsers
   *
   * @param auth        The instance of {@link StitchAuth} where the user was removed.
   *                    It can be used to infer the current state of authentication.
   * @param removedUser The user that was removed.
   */
  default void onUserRemoved(final StitchAuth auth, final StitchUser removedUser) {

  }

  /**
   * Called whenever this listener is registered for the first time.
   * <p>
   * This can be useful to infer the state of authentication, because any events 
   * that occurred before the listener was registered will not be seen by the
   * listener.
   * </p>
   * @see StitchAuth#addAuthListener
   * @param auth The instance of {@link StitchAuth} where the listener was registered.
   *             It can be used to infer the current state of authentication.
   */
  default void onListenerRegistered(final StitchAuth auth) {

  }
}
