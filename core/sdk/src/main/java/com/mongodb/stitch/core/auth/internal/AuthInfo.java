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

import static com.mongodb.stitch.core.auth.internal.models.StoreAuthInfo.ACTIVE_USER_STORAGE_NAME;
import static com.mongodb.stitch.core.auth.internal.models.StoreAuthInfo.ALL_USERS_STORAGE_NAME;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mongodb.stitch.core.auth.internal.models.ApiAuthInfo;
import com.mongodb.stitch.core.auth.internal.models.StoreAuthInfo;
import com.mongodb.stitch.core.internal.common.StitchObjectMapper;
import com.mongodb.stitch.core.internal.common.Storage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/** AuthInfo describes the authentication state of a user and the SDK. */
public class AuthInfo {
  private final String userId;
  private final String deviceId;
  private final String accessToken;
  private final String refreshToken;
  private final String loggedInProviderType;
  private final String loggedInProviderName;
  private final StitchUserProfileImpl userProfile;

  /**
   * Constructs a new AuthInfo that's fully specified.
   *
   * @param userId the id of the currently logged in user.
   * @param deviceId the id of the device this SDK is running on.
   * @param accessToken the access token associated with the user.
   * @param refreshToken the refresh token associated with the user.
   * @param loggedInProviderType the type of auth provider the current user logged in with.
   * @param loggedInProviderName the name of the auth provider the current user logged in with.
   * @param userProfile the profile information about the currently logged in user.
   */
  public AuthInfo(
      final String userId,
      final String deviceId,
      final String accessToken,
      final String refreshToken,
      final String loggedInProviderType,
      final String loggedInProviderName,
      final StitchUserProfileImpl userProfile) {
    this.userId = userId;
    this.deviceId = deviceId;
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
    this.loggedInProviderType = loggedInProviderType;
    this.loggedInProviderName = loggedInProviderName;
    this.userProfile = userProfile;
  }

  static AuthInfo empty() {
    return new AuthInfo(null, null, null, null, null, null, null);
  }

  static AuthInfo readFromApi(final InputStream is) throws IOException {
    return StitchObjectMapper.getInstance().readValue(is, ApiAuthInfo.class);
  }

  static AuthInfo readActiveUserFromStorage(final Storage storage) throws IOException {
    final String rawInfo = storage.get(ACTIVE_USER_STORAGE_NAME);
    if (rawInfo == null) {
      return null;
    }

    return StitchObjectMapper.getInstance().readValue(rawInfo, StoreAuthInfo.class);
  }

  static LinkedList<AuthInfo> readCurrentUsersFromStorage(
      final Storage storage
  ) throws IOException {
    final String rawInfo = storage.get(ALL_USERS_STORAGE_NAME);
    if (rawInfo == null) {
      return null;
    }

    return StitchObjectMapper.getInstance().readValue(
        rawInfo,
        new TypeReference<LinkedList<StoreAuthInfo>>(){});
  }

  static void writeActiveUserAuthInfoToStorage(final AuthInfo authInfo,
                                               final Storage storage) throws IOException {
    final StoreAuthInfo info =
        new StoreAuthInfo(
            authInfo.userId,
            authInfo.deviceId,
            authInfo.accessToken,
            authInfo.refreshToken,
            authInfo.loggedInProviderType,
            authInfo.loggedInProviderName,
            authInfo.userProfile
        );

    final String rawInfo = StitchObjectMapper.getInstance().writeValueAsString(info);
    storage.set(ACTIVE_USER_STORAGE_NAME, rawInfo);
  }

  static void writeLoggedInUsersAuthInfoToStorage(
      final LinkedList<AuthInfo> loggedInUsersAuthInfo,
      final Storage storage
  ) throws IOException {
    final List<AuthInfo> authInfos = new ArrayList<>();
    for (final AuthInfo authInfo : loggedInUsersAuthInfo) {
      authInfos.add(new StoreAuthInfo(
          authInfo.userId,
          authInfo.deviceId,
          authInfo.accessToken,
          authInfo.refreshToken,
          authInfo.loggedInProviderType,
          authInfo.loggedInProviderName,
          authInfo.userProfile));
    }

    final String rawInfo = StitchObjectMapper.getInstance().writeValueAsString(authInfos);
    storage.set(ALL_USERS_STORAGE_NAME, rawInfo);
  }

  AuthInfo loggedOut() {
    return new AuthInfo(
        userId, deviceId, null, null, loggedInProviderType, loggedInProviderName, userProfile);
  }

  AuthInfo merge(final AuthInfo newInfo) {
    return new AuthInfo(
        newInfo.userId == null ? userId : newInfo.userId,
        newInfo.deviceId == null ? deviceId : newInfo.deviceId,
        newInfo.accessToken == null ? accessToken : newInfo.accessToken,
        newInfo.refreshToken == null ? refreshToken : newInfo.refreshToken,
        newInfo.loggedInProviderType == null ? loggedInProviderType : newInfo.loggedInProviderType,
        newInfo.loggedInProviderName == null ? loggedInProviderName : newInfo.loggedInProviderName,
        newInfo.userProfile == null ? userProfile : newInfo.userProfile);
  }

  public String getUserId() {
    return userId;
  }

  public String getDeviceId() {
    return deviceId;
  }

  public String getAccessToken() {
    return accessToken;
  }

  public String getRefreshToken() {
    return refreshToken;
  }

  public String getLoggedInProviderType() {
    return loggedInProviderType;
  }

  public String getLoggedInProviderName() {
    return loggedInProviderName;
  }

  public StitchUserProfileImpl getUserProfile() {
    return userProfile;
  }

  public boolean isLoggedIn() {
    return accessToken != null && refreshToken != null;
  }

  @Override
  public int hashCode() {
    return this.userId.hashCode();
  }

  @Override
  public boolean equals(final Object o) {
    if (!(o instanceof AuthInfo)) {
      return false;
    }

    final AuthInfo authInfo = (AuthInfo) o;
    return authInfo.getUserId().equals(getUserId())
        && authInfo.getDeviceId().equals(getDeviceId())
        && authInfo.getLoggedInProviderName().equals(getLoggedInProviderName())
        &&  authInfo.getLoggedInProviderType().equals(getLoggedInProviderType());
  }
}
