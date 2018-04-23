package com.mongodb.stitch.server.core.auth.providers;

import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;

public interface NamedAuthProviderClientSupplier<T> {
  T getClient(
      final String providerName,
      final StitchRequestClient requestClient,
      final StitchAuthRoutes routes);
}
