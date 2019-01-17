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

package com.mongodb.stitch.core.auth.internal;

import com.mongodb.stitch.core.auth.StitchUserIdentity;
import com.mongodb.stitch.core.auth.StitchUserProfile;
import com.mongodb.stitch.core.auth.UserType;

import java.util.List;

public abstract class CoreStitchUserImpl implements CoreStitchUser {
  private final String id;
  private final String deviceId;
  private final String loggedInProviderType;
  private final String loggedInProviderName;
  private final StitchUserProfileImpl profile;

  protected CoreStitchUserImpl(
      final String id,
      final String deviceId,
      final String loggedInProviderType,
      final String loggedInProviderName,
      final StitchUserProfileImpl profile) {
    this.id = id;
    this.deviceId = deviceId;
    this.loggedInProviderType = loggedInProviderType;
    this.loggedInProviderName = loggedInProviderName;
    this.profile = profile == null ? StitchUserProfileImpl.empty() : profile;
  }

  public String getId() {
    return id;
  }

  public String getDeviceId() {
    return deviceId;
  }

  public String getLoggedInProviderType() {
    return loggedInProviderType;
  }

  public String getLoggedInProviderName() {
    return loggedInProviderName;
  }

  public UserType getUserType() {
    return profile.getUserType();
  }

  public StitchUserProfile getProfile() {
    return profile;
  }

  public List<? extends StitchUserIdentity> getIdentities() {
    return profile.getIdentities();
  }

  @Override
  public int hashCode() {
    return this.id.hashCode();
  }

  @Override
  public boolean equals(final Object o) {
    return o instanceof CoreStitchUser
        && ((CoreStitchUser) o).getId().equals(getId())
        && ((CoreStitchUser) o).getDeviceId().equals(getDeviceId())
        && ((CoreStitchUser) o).getLoggedInProviderName().equals(getLoggedInProviderName())
        &&  ((CoreStitchUser) o).getLoggedInProviderType().equals(getLoggedInProviderType());
  }
}
