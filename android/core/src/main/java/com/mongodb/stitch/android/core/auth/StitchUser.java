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
import com.mongodb.stitch.core.auth.StitchCredential;
import com.mongodb.stitch.core.auth.internal.CoreStitchUser;

/**
 * A user that belongs to a MongoDB Stitch application.
 * <p>
 * The logged-in, active user can be obtained from a {@link StitchAuth} instance
 * with the {@link StitchAuth#getUser} method.
 * </p><p>
 * Use {@link StitchAuth#listUsers} to get a list of the users who are on the device.
 * </p>
 * 
 * @see StitchAuth
 */
public interface StitchUser extends CoreStitchUser {

  /**
   * Links this user with another identity represented by the given credential.
   * <p>
   * Linking allows one user to become associated with another credential. For example,
   * suppose a user logs in anonymously to try the app before deciding to create an
   * account. This method allows the data associated with the anonymous credential
   * to be associated with the new account credential.
   * </p>
   * @param credential the credential bound to an identity to link to.
   * @return a {@link Task} containing the newly linked user with a new identity added associated
   *         with the given credential.
   */
  Task<StitchUser> linkWithCredential(final StitchCredential credential);
}
