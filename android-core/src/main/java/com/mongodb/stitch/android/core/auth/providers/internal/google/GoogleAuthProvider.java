package com.mongodb.stitch.android.core.auth.providers.internal.google;

import com.mongodb.stitch.android.core.auth.providers.internal.AuthProviderClientSupplier;
import com.mongodb.stitch.android.core.auth.providers.internal.google.internal.GoogleAuthProviderClientImpl;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;

public final class GoogleAuthProvider {
  public static final AuthProviderClientSupplier<GoogleAuthProviderClient> ClientProvider =
      new AuthProviderClientSupplier<GoogleAuthProviderClient>() {
        @Override
        public GoogleAuthProviderClient getClient(
            StitchRequestClient requestClient, StitchAuthRoutes routes, TaskDispatcher dispatcher) {
          return new GoogleAuthProviderClientImpl();
        }
      };
}
