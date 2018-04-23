package com.mongodb.stitch.server.core.auth;

import com.mongodb.stitch.core.auth.StitchCredential;
import com.mongodb.stitch.server.core.auth.providers.AuthProviderClientSupplier;
import com.mongodb.stitch.server.core.auth.providers.NamedAuthProviderClientSupplier;

public interface StitchAuth {
  <T> T getProviderClient(final AuthProviderClientSupplier<T> provider);

  <T> T getProviderClient(
      final NamedAuthProviderClientSupplier<T> provider, final String providerName);

  StitchUser loginWithCredential(final StitchCredential credential);

  void logout();

  boolean isLoggedIn();

  StitchUser getUser();
}
