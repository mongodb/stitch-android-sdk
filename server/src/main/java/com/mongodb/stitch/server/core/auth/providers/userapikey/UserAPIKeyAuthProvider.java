package com.mongodb.stitch.server.core.auth.providers.userapikey;

import com.mongodb.stitch.server.core.auth.providers.AuthProviderClientSupplier;
import com.mongodb.stitch.server.core.auth.providers.userapikey.internal.UserAPIKeyAuthProviderClientImpl;

public final class UserAPIKeyAuthProvider {
  public static final AuthProviderClientSupplier<UserAPIKeyAuthProviderClient> ClientProvider =
      (requestClient, routes) -> new UserAPIKeyAuthProviderClientImpl();
}
