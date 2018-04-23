package com.mongodb.stitch.server.core.auth.providers.custom;

import com.mongodb.stitch.server.core.auth.providers.AuthProviderClientSupplier;
import com.mongodb.stitch.server.core.auth.providers.custom.internal.CustomAuthProviderClientImpl;

public final class CustomAuthProvider {
  public static final AuthProviderClientSupplier<CustomAuthProviderClient> ClientProvider =
      (requestClient, routes) -> new CustomAuthProviderClientImpl();
}
