package com.mongodb.stitch.core.auth.internal;

import static com.mongodb.stitch.core.auth.internal.models.StoreAuthInfo.STORAGE_NAME;

import com.mongodb.stitch.core.auth.internal.models.APIAuthInfo;
import com.mongodb.stitch.core.auth.internal.models.StoreAuthInfo;
import com.mongodb.stitch.core.internal.common.StitchObjectMapper;
import com.mongodb.stitch.core.internal.common.Storage;
import java.io.IOException;
import java.io.InputStream;

public class AuthInfo {
  protected final String userId;
  protected final String deviceId;
  protected final String accessToken;
  protected final String refreshToken;
  protected final String loggedInProviderType;
  protected final String loggedInProviderName;
  protected final StitchUserProfileImpl userProfile;

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

  static AuthInfo readFromAPI(final InputStream is) throws IOException {
    return StitchObjectMapper.getInstance().readValue(is, APIAuthInfo.class);
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
}
