package com.mongodb.stitch.core.auth.providers;

import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;

public abstract class CoreAuthProviderClient {

  protected final String providerName;
  protected final StitchRequestClient requestClient;
  protected final StitchAuthRoutes authRoutes;

  @SuppressWarnings("unused")
  protected CoreAuthProviderClient(final CoreAuthProviderClient coreClient) {
    this.providerName = coreClient.providerName;
    this.requestClient = coreClient.requestClient;
    this.authRoutes = coreClient.authRoutes;
  }

  protected CoreAuthProviderClient(
      final String providerName,
      final StitchRequestClient requestClient,
      final StitchAuthRoutes authRoutes) {
    this.providerName = providerName;
    this.requestClient = requestClient;
    this.authRoutes = authRoutes;
  }
}
