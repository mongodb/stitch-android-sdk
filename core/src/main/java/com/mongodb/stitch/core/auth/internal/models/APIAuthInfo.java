package com.mongodb.stitch.core.auth.internal.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongodb.stitch.core.auth.internal.AuthInfo;

public class APIAuthInfo extends AuthInfo {
  @JsonCreator
  private APIAuthInfo(
      @JsonProperty(Fields.USER_ID) final String userId,
      @JsonProperty(Fields.DEVICE_ID) final String deviceId,
      @JsonProperty(Fields.ACCESS_TOKEN) final String accessToken,
      @JsonProperty(Fields.REFRESH_TOKEN) final String refreshToken) {
    super(userId, deviceId, accessToken, refreshToken, null, null, null);
  }

  @JsonProperty(Fields.USER_ID)
  public String getUserId() {
    return this.userId;
  }

  @JsonProperty(Fields.DEVICE_ID)
  public String getDeviceId() {
    return this.deviceId;
  }

  @JsonProperty(Fields.ACCESS_TOKEN)
  public String getAccessToken() {
    return this.accessToken;
  }

  @JsonProperty(Fields.REFRESH_TOKEN)
  public String getRefreshToken() {
    return this.refreshToken;
  }

  private static class Fields {
    private static final String USER_ID = "user_id";
    private static final String DEVICE_ID = "device_id";
    private static final String ACCESS_TOKEN = "access_token";
    private static final String REFRESH_TOKEN = "refresh_token";
  }
}
