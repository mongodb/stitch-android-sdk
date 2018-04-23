package com.mongodb.stitch.android.core.auth.providers.internal;

import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;

public interface AuthProviderClientSupplier<T> {
  T getClient(
      final StitchRequestClient requestClient,
      final StitchAuthRoutes routes,
      final TaskDispatcher dispatcher);
}
