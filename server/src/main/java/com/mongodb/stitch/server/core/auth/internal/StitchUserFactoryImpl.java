package com.mongodb.stitch.server.core.auth.internal;

import com.mongodb.stitch.core.auth.internal.StitchUserFactory;
import com.mongodb.stitch.core.auth.internal.StitchUserProfileImpl;
import com.mongodb.stitch.server.core.auth.StitchUser;

public final class StitchUserFactoryImpl implements StitchUserFactory<StitchUser> {

  private final StitchAuthImpl auth;

  public StitchUserFactoryImpl(final StitchAuthImpl auth) {
    this.auth = auth;
  }

  @Override
  public StitchUser makeUser(
      final String id,
      final String loggedInProviderType,
      final String loggedInProviderName,
      final StitchUserProfileImpl userProfile) {
    return new StitchUserImpl(id, loggedInProviderType, loggedInProviderName, userProfile, auth);
  }
}
