package com.mongodb.stitch.server.core.auth.internal;

import com.mongodb.stitch.core.auth.StitchCredential;
import com.mongodb.stitch.core.auth.internal.CoreStitchUserImpl;
import com.mongodb.stitch.core.auth.internal.StitchUserProfileImpl;
import com.mongodb.stitch.server.core.auth.StitchUser;

public final class StitchUserImpl extends CoreStitchUserImpl implements StitchUser {
  private final StitchAuthImpl auth;

  public StitchUserImpl(
      final String id,
      final String loggedInProviderType,
      final String loggedInProviderName,
      final StitchUserProfileImpl profile,
      final StitchAuthImpl auth) {
    super(id, loggedInProviderType, loggedInProviderName, profile);
    this.auth = auth;
  }

  @Override
  public StitchUser linkWithCredential(StitchCredential credential) {
    return auth.linkWithCredential(this, credential);
  }
}
