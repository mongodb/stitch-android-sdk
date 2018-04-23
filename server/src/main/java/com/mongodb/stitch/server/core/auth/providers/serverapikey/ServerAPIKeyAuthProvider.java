package com.mongodb.stitch.server.core.auth.providers.serverapikey;

import com.mongodb.stitch.server.core.auth.providers.AuthProviderClientSupplier;
import com.mongodb.stitch.server.core.auth.providers.serverapikey.internal.ServerAPIKeyAuthProviderClientImpl;

public final class ServerAPIKeyAuthProvider {
  public static final AuthProviderClientSupplier<ServerAPIKeyAuthProviderClient> ClientProvider =
      (requestClient, routes) -> new ServerAPIKeyAuthProviderClientImpl();
}
