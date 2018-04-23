package com.mongodb.stitch.server.core.auth.providers.facebook;

import com.mongodb.stitch.server.core.auth.providers.AuthProviderClientSupplier;
import com.mongodb.stitch.server.core.auth.providers.facebook.internal.FacebookAuthProviderClientImpl;

public final class FacebookAuthProvider {
  public static final AuthProviderClientSupplier<FacebookAuthProviderClient> ClientProvider =
      (requestClient, routes) -> new FacebookAuthProviderClientImpl();
}
