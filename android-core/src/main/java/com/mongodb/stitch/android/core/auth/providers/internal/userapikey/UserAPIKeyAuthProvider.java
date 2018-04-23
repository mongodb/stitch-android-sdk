package com.mongodb.stitch.android.core.auth.providers.internal.userapikey;

import com.mongodb.stitch.android.core.auth.providers.internal.AuthProviderClientSupplier;
import com.mongodb.stitch.android.core.auth.providers.internal.userapikey.internal.UserAPIKeyAuthProviderClientImpl;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;

public final class UserAPIKeyAuthProvider {
  public static final AuthProviderClientSupplier<UserAPIKeyAuthProviderClient> ClientProvider =
      new AuthProviderClientSupplier<UserAPIKeyAuthProviderClient>() {
        @Override
        public UserAPIKeyAuthProviderClient getClient(
            StitchRequestClient requestClient, StitchAuthRoutes routes, TaskDispatcher dispatcher) {
          return new UserAPIKeyAuthProviderClientImpl();
        }
      };
}
