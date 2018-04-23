package com.mongodb.stitch.android.core.auth.internal;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.auth.StitchUser;
import com.mongodb.stitch.core.auth.StitchCredential;
import com.mongodb.stitch.core.auth.internal.CoreStitchUserImpl;
import com.mongodb.stitch.core.auth.internal.StitchUserProfileImpl;

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
  public Task<StitchUser> linkWithCredential(StitchCredential credential) {
    return auth.linkWithCredential(this, credential);
  }
}
