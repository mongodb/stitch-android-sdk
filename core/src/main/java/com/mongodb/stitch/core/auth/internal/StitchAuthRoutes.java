package com.mongodb.stitch.core.auth.internal;

public interface StitchAuthRoutes {
  String getSessionRoute();

  String getProfileRoute();

  String getAuthProviderRoute(final String providerName);

  String getAuthProviderLoginRoute(final String providerName);

  String getAuthProviderLinkRoute(final String providerName);
}
