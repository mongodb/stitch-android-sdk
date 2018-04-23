package com.mongodb.stitch.android.core.auth.providers.internal.anonymous;

import com.mongodb.stitch.android.core.auth.providers.internal.AuthProviderClientSupplier;
import com.mongodb.stitch.android.core.auth.providers.internal.anonymous.internal.AnonymousAuthProviderClientImpl;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;

public final class AnonymousAuthProvider {
  public static final AuthProviderClientSupplier<AnonymousAuthProviderClient> ClientProvider =
      new AuthProviderClientSupplier<AnonymousAuthProviderClient>() {
        @Override
        public AnonymousAuthProviderClient getClient(
            StitchRequestClient requestClient, StitchAuthRoutes routes, TaskDispatcher dispatcher) {
          return new AnonymousAuthProviderClientImpl();
        }
      };
}
