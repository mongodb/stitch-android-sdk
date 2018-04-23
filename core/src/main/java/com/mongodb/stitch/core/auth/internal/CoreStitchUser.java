package com.mongodb.stitch.core.auth.internal;

import com.mongodb.stitch.core.auth.StitchUserIdentity;
import com.mongodb.stitch.core.auth.StitchUserProfile;
import java.util.List;

public interface CoreStitchUser {
  String getId();

  String getLoggedInProviderType();

  String getLoggedInProviderName();

  String getUserType();

  StitchUserProfile getProfile();

  List<? extends StitchUserIdentity> getIdentities();
}
