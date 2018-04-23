package com.mongodb.stitch.server.core.auth.providers.google;

import com.mongodb.stitch.server.core.auth.providers.AuthProviderClientSupplier;
import com.mongodb.stitch.server.core.auth.providers.google.internal.GoogleAuthProviderClientImpl;

public final class GoogleAuthProvider {
  public static final AuthProviderClientSupplier<GoogleAuthProviderClient> ClientProvider =
      (requestClient, routes) -> new GoogleAuthProviderClientImpl();
}
