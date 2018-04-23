package com.mongodb.stitch.android.core.auth.providers.internal.custom;

import com.mongodb.stitch.android.core.auth.providers.internal.AuthProviderClientSupplier;
import com.mongodb.stitch.android.core.auth.providers.internal.custom.internal.CustomAuthProviderClientImpl;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;

public final class CustomAuthProvider {
  public static final AuthProviderClientSupplier<CustomAuthProviderClient> ClientProvider =
      new AuthProviderClientSupplier<CustomAuthProviderClient>() {
        @Override
        public CustomAuthProviderClient getClient(
            StitchRequestClient requestClient, StitchAuthRoutes routes, TaskDispatcher dispatcher) {
          return new CustomAuthProviderClientImpl();
        }
      };
}
