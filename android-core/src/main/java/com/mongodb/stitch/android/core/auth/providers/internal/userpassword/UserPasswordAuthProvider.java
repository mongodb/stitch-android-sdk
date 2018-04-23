package com.mongodb.stitch.android.core.auth.providers.internal.userpassword;

import com.mongodb.stitch.android.core.auth.providers.internal.AuthProviderClientSupplier;
import com.mongodb.stitch.android.core.auth.providers.internal.userpassword.internal.UserPasswordAuthProviderClientImpl;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;

public final class UserPasswordAuthProvider {
  public static final AuthProviderClientSupplier<UserPasswordAuthProviderClient> ClientProvider =
      new AuthProviderClientSupplier<UserPasswordAuthProviderClient>() {
        @Override
        public UserPasswordAuthProviderClient getClient(
            StitchRequestClient requestClient, StitchAuthRoutes routes, TaskDispatcher dispatcher) {
          return new UserPasswordAuthProviderClientImpl(requestClient, routes, dispatcher);
        }
      };
}
