package com.mongodb.stitch.core.auth.internal;

import com.mongodb.stitch.core.auth.StitchUserIdentity;
import com.mongodb.stitch.core.auth.StitchUserProfile;
import java.util.List;

public abstract class CoreStitchUserImpl implements CoreStitchUser {
  private final String id;
  private final String loggedInProviderType;
  private final String loggedInProviderName;
  private final StitchUserProfileImpl profile;

  protected CoreStitchUserImpl(
      final String id,
      final String loggedInProviderType,
      final String loggedInProviderName,
      final StitchUserProfileImpl profile) {
    this.id = id;
    this.loggedInProviderType = loggedInProviderType;
    this.loggedInProviderName = loggedInProviderName;
    this.profile = profile == null ? StitchUserProfileImpl.empty() : profile;
  }

  public String getId() {
    return id;
  }

  public String getLoggedInProviderType() {
    return loggedInProviderType;
  }

  public String getLoggedInProviderName() {
    return loggedInProviderName;
  }

  public String getUserType() {
    return profile.getUserType();
  }

  public StitchUserProfile getProfile() {
    return profile;
  }

  public List<? extends StitchUserIdentity> getIdentities() {
    return profile.getIdentities();
  }
}
