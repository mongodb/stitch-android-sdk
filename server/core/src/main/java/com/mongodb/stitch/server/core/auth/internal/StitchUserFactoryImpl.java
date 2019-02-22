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

package com.mongodb.stitch.server.core.auth.internal;

import com.mongodb.stitch.core.auth.internal.StitchUserFactory;
import com.mongodb.stitch.core.auth.internal.StitchUserProfileImpl;
import com.mongodb.stitch.server.core.auth.StitchUser;

import java.util.Date;

public final class StitchUserFactoryImpl implements StitchUserFactory<StitchUser> {

  private final StitchAuthImpl auth;

  public StitchUserFactoryImpl(final StitchAuthImpl auth) {
    this.auth = auth;
  }

  @Override
  public StitchUser makeUser(
      final String id,
      final String deviceId,
      final String loggedInProviderType,
      final String loggedInProviderName,
      final StitchUserProfileImpl userProfile,
      final boolean isLoggedIn,
      final Date lastAuthActivity) {
    return new StitchUserImpl(id, deviceId, loggedInProviderType, loggedInProviderName,
            userProfile, auth, isLoggedIn, lastAuthActivity);
  }
}
