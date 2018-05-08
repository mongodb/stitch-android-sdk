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

import static com.mongodb.stitch.core.auth.internal.models.StoreAuthInfo.STORAGE_NAME;

import com.mongodb.stitch.core.auth.internal.models.ApiAuthInfo;
import com.mongodb.stitch.core.auth.internal.models.StoreAuthInfo;
import com.mongodb.stitch.core.internal.common.StitchObjectMapper;
import com.mongodb.stitch.core.internal.common.Storage;
import java.io.IOException;
import java.io.InputStream;

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
   * @param userId The id of the currently logged in user.
   * @param deviceId The id of the device this SDK is running on.
   * @param accessToken The access token associated with the user.
   * @param refreshToken The refresh token associated with the user.
   * @param loggedInProviderType The type of auth provider the current user logged in with.
   * @param loggedInProviderName The name of the auth provider the current user logged in with.
   * @param userProfile The profile information about the currently logged in user.
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

  static AuthInfo readFromStorage(final Storage storage) throws IOException {
    final String rawInfo = storage.get(STORAGE_NAME);
    if (rawInfo == null) {
      return null;
    }
    return StitchObjectMapper.getInstance().readValue(rawInfo, StoreAuthInfo.class);
  }

  void writeToStorage(final Storage storage) throws IOException {
    final StoreAuthInfo info =
        new StoreAuthInfo(
            userId,
            deviceId,
            accessToken,
            refreshToken,
            loggedInProviderType,
            loggedInProviderName,
            userProfile);
    final String rawInfo = StitchObjectMapper.getInstance().writeValueAsString(info);
    storage.set(STORAGE_NAME, rawInfo);
  }

  AuthInfo loggedOut() {
    return new AuthInfo(null, deviceId, null, null, null, null, null);
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
}
