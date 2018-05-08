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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongodb.stitch.core.auth.internal.AuthInfo;

public final class ApiAuthInfo extends AuthInfo {
  @JsonCreator
  private ApiAuthInfo(
      @JsonProperty(Fields.USER_ID) final String userId,
      @JsonProperty(Fields.DEVICE_ID) final String deviceId,
      @JsonProperty(Fields.ACCESS_TOKEN) final String accessToken,
      @JsonProperty(Fields.REFRESH_TOKEN) final String refreshToken) {
    super(userId, deviceId, accessToken, refreshToken, null, null, null);
  }

  @JsonProperty(Fields.USER_ID)
  public String getUserId() {
    return super.getUserId();
  }

  @JsonProperty(Fields.DEVICE_ID)
  public String getDeviceId() {
    return super.getDeviceId();
  }

  @JsonProperty(Fields.ACCESS_TOKEN)
  public String getAccessToken() {
    return super.getAccessToken();
  }

  @JsonProperty(Fields.REFRESH_TOKEN)
  public String getRefreshToken() {
    return super.getRefreshToken();
  }

  private static class Fields {
    private static final String USER_ID = "user_id";
    private static final String DEVICE_ID = "device_id";
    private static final String ACCESS_TOKEN = "access_token";
    private static final String REFRESH_TOKEN = "refresh_token";
  }
}
