package com.mongodb.stitch.core.internal.net;

import static com.mongodb.stitch.core.internal.net.StitchAppAuthRoutes.RouteParts.AUTH_PROVIDER_LINK_ROUTE;
import static com.mongodb.stitch.core.internal.net.StitchAppAuthRoutes.RouteParts.AUTH_PROVIDER_LOGIN_ROUTE;
import static com.mongodb.stitch.core.internal.net.StitchAppAuthRoutes.RouteParts.AUTH_PROVIDER_ROUTE;
import static com.mongodb.stitch.core.internal.net.StitchAppAuthRoutes.RouteParts.PROFILE_ROUTE;
import static com.mongodb.stitch.core.internal.net.StitchAppAuthRoutes.RouteParts.SESSION_ROUTE;
import static com.mongodb.stitch.core.internal.net.StitchAppRoutes.RouteParts.APP_ROUTE;
import static com.mongodb.stitch.core.internal.net.StitchAppRoutes.RouteParts.BASE_ROUTE;

import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes;

public final class StitchAppAuthRoutes implements StitchAuthRoutes {
  private final String clientAppId;

  StitchAppAuthRoutes(final String clientAppId) {
    this.clientAppId = clientAppId;
  }

  @Override
  public String getSessionRoute() {
    return SESSION_ROUTE;
  }

  @Override
  public String getProfileRoute() {
    return PROFILE_ROUTE;
  }

  @Override
  public String getAuthProviderRoute(String providerName) {
    return String.format(AUTH_PROVIDER_ROUTE, clientAppId, providerName);
  }

  @Override
  public String getAuthProviderLoginRoute(String providerName) {
    return String.format(AUTH_PROVIDER_LOGIN_ROUTE, clientAppId, providerName);
  }

  @Override
  public String getAuthProviderLinkRoute(String providerName) {
    return String.format(AUTH_PROVIDER_LINK_ROUTE, clientAppId, providerName);
  }

  public static class RouteParts {
    static final String SESSION_ROUTE = BASE_ROUTE + "/auth/session";
    static final String PROFILE_ROUTE = BASE_ROUTE + "/auth/profile";
    static final String AUTH_PROVIDER_ROUTE = APP_ROUTE + "/auth/providers/%s";
    static final String AUTH_PROVIDER_LOGIN_ROUTE = AUTH_PROVIDER_ROUTE + "/login";
    static final String AUTH_PROVIDER_LINK_ROUTE = AUTH_PROVIDER_LOGIN_ROUTE + "?link=true";
  }
}
