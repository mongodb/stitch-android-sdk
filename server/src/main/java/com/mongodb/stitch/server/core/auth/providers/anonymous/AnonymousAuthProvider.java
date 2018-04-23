package com.mongodb.stitch.server.core.auth.providers.anonymous;

import com.mongodb.stitch.server.core.auth.providers.AuthProviderClientSupplier;
import com.mongodb.stitch.server.core.auth.providers.anonymous.internal.AnonymousAuthProviderClientImpl;

public final class AnonymousAuthProvider {
  public static final AuthProviderClientSupplier<AnonymousAuthProviderClient> ClientProvider =
      (requestClient, routes) -> new AnonymousAuthProviderClientImpl();
}
