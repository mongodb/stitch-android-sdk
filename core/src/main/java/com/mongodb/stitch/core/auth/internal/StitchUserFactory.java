package com.mongodb.stitch.core.auth.internal;

public interface StitchUserFactory<T extends CoreStitchUser> {
  T makeUser(
      final String id,
      final String loggedInProviderType,
      final String loggedInProviderName,
      final StitchUserProfileImpl userProfile);
}
