package com.mongodb.stitch.android.core.auth.providers.internal.serverapikey;

import com.mongodb.stitch.android.core.auth.providers.internal.AuthProviderClientSupplier;
import com.mongodb.stitch.android.core.auth.providers.internal.serverapikey.internal.ServerAPIKeyAuthProviderClientImpl;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;

public final class ServerAPIKeyAuthProvider {
  public static final AuthProviderClientSupplier<ServerAPIKeyAuthProviderClient> ClientProvider =
      new AuthProviderClientSupplier<ServerAPIKeyAuthProviderClient>() {
        @Override
        public ServerAPIKeyAuthProviderClient getClient(
            StitchRequestClient requestClient, StitchAuthRoutes routes, TaskDispatcher dispatcher) {
          return new ServerAPIKeyAuthProviderClientImpl();
        }
      };
}
