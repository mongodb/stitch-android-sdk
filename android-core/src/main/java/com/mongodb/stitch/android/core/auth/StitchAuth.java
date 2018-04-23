package com.mongodb.stitch.android.core.auth;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.auth.providers.internal.AuthProviderClientSupplier;
import com.mongodb.stitch.android.core.auth.providers.internal.NamedAuthProviderClientSupplier;
import com.mongodb.stitch.core.auth.StitchCredential;
import java.io.Closeable;

public interface StitchAuth extends Closeable {
  <T> T getProviderClient(final AuthProviderClientSupplier<T> provider);

  <T> T getProviderClient(
      final NamedAuthProviderClientSupplier<T> provider, final String providerName);

  Task<StitchUser> loginWithCredential(final StitchCredential credential);

  Task<Void> logout();

  boolean isLoggedIn();

  StitchUser getUser();

  void addAuthListener(final StitchAuthListener listener);

  void removeAuthListener(final StitchAuthListener listener);
}
