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

package com.mongodb.stitch.core.auth.internal.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongodb.stitch.core.auth.internal.AuthInfo;
import com.mongodb.stitch.core.auth.internal.StitchUserProfileImpl;

/**
 * An {@link AuthInfo} for local persistence.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class StoreAuthInfo extends AuthInfo {

  public static final String ALL_USERS_STORAGE_NAME = "all_auth_infos";
  public static final String ACTIVE_USER_STORAGE_NAME = "auth_info";

  /**
   * Constructs a {@link StoreAuthInfo} from storage.
   *
   * @param userId the id of the user.
   * @param deviceId the device id of the user.
   * @param accessToken the current access token for the logged in user.
   * @param refreshToken the current refresh token for the logged in user.
   * @param loggedInProviderType the type of provider used to log in the current user.
   * @param loggedInProviderName the name of the provider used to log in the current user.
   * @param userProfile profile information about the user.
   */
  @JsonCreator
  public StoreAuthInfo(
      @JsonProperty(Fields.USER_ID) final String userId,
      @JsonProperty(Fields.DEVICE_ID) final String deviceId,
      @JsonProperty(Fields.ACCESS_TOKEN) final String accessToken,
      @JsonProperty(Fields.REFRESH_TOKEN) final String refreshToken,
      @JsonProperty(Fields.LOGGED_IN_PROVIDER_TYPE) final String loggedInProviderType,
      @JsonProperty(Fields.LOGGED_IN_PROVIDER_NAME) final String loggedInProviderName,
      @JsonProperty(Fields.USER_PROFILE) final StoreCoreUserProfile userProfile) {
    super(
        userId,
        deviceId,
        accessToken,
        refreshToken,
        loggedInProviderType,
        loggedInProviderName,
        userProfile);
  }

  /**
   * Constructs a fully specified {@link StoreAuthInfo}.
   *
   * @param userId the id of the user.
   * @param deviceId the device id of the user.
   * @param accessToken the current access token for the logged in user.
   * @param refreshToken the current refresh token for the logged in user.
   * @param loggedInProviderType the type of provider used to log in the current user.
   * @param loggedInProviderName the name of the provider used to log in the current user.
   * @param userProfile profile information about the user.
   */
  public StoreAuthInfo(
      final String userId,
      final String deviceId,
      final String accessToken,
      final String refreshToken,
      final String loggedInProviderType,
      final String loggedInProviderName,
      final StitchUserProfileImpl userProfile) {
    super(
        userId,
        deviceId,
        accessToken,
        refreshToken,
        loggedInProviderType,
        loggedInProviderName,
        userProfile);
  }

  @JsonProperty(Fields.USER_ID)
  private String getUserIdValue() {
    return getUserId();
  }

  @JsonProperty(Fields.DEVICE_ID)
  private String getDeviceIdValue() {
    return getDeviceId();
  }

  @JsonProperty(Fields.ACCESS_TOKEN)
  private String getAccessTokenValue() {
    return getAccessToken();
  }

  @JsonProperty(Fields.REFRESH_TOKEN)
  private String getRefreshTokenValue() {
    return getRefreshToken();
  }

  @JsonProperty(Fields.LOGGED_IN_PROVIDER_TYPE)
  private String getLoggedInProviderTypeValue() {
    return getLoggedInProviderType();
  }

  @JsonProperty(Fields.LOGGED_IN_PROVIDER_NAME)
  private String getLoggedInProviderNameValue() {
    return getLoggedInProviderName();
  }

  @JsonProperty(Fields.USER_PROFILE)
  private StoreCoreUserProfile getUserProfileValue() {
    if (getUserProfile() == null) {
      return null;
    }
    return new StoreCoreUserProfile(getUserProfile());
  }

  private static class Fields {
    private static final String USER_ID = "user_id";
    private static final String DEVICE_ID = "device_id";
    private static final String ACCESS_TOKEN = "access_token";
    private static final String REFRESH_TOKEN = "refresh_token";
    private static final String LOGGED_IN_PROVIDER_TYPE = "logged_in_provider_type";
    private static final String LOGGED_IN_PROVIDER_NAME = "logged_in_provider_name";
    private static final String USER_PROFILE = "user_profile";
  }
}
