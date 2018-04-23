package com.mongodb.stitch.android.core.auth.providers.internal.facebook;

import com.mongodb.stitch.android.core.auth.providers.internal.AuthProviderClientSupplier;
import com.mongodb.stitch.android.core.auth.providers.internal.facebook.internal.FacebookAuthProviderClientImpl;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;

public final class FacebookAuthProvider {
  public static final AuthProviderClientSupplier<FacebookAuthProviderClient> ClientProvider =
      new AuthProviderClientSupplier<FacebookAuthProviderClient>() {
        @Override
        public FacebookAuthProviderClient getClient(
            StitchRequestClient requestClient, StitchAuthRoutes routes, TaskDispatcher dispatcher) {
          return new FacebookAuthProviderClientImpl();
        }
      };
}
