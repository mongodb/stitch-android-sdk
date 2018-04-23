package com.mongodb.stitch.core.internal.net;

import com.mongodb.stitch.core.services.internal.StitchServiceRoutes;

public final class StitchAppRoutes {
  private final String clientAppId;
  private final StitchAppAuthRoutes authRoutes;
  private final StitchServiceRoutes serviceRoutes;

  public StitchAppRoutes(final String clientAppId) {
    this.clientAppId = clientAppId;
    authRoutes = new StitchAppAuthRoutes(clientAppId);
    serviceRoutes = new StitchServiceRoutes(clientAppId);
  }

  public StitchAppAuthRoutes getAuthRoutes() {
    return authRoutes;
  }

  public StitchServiceRoutes getServiceRoutes() {
    return serviceRoutes;
  }

  public String getFunctionCallRoute() {
    return String.format(RouteParts.FUNCTION_CALL_ROUTE, clientAppId);
  }

  public static class RouteParts {
    static final String BASE_ROUTE = "/api/client/v2.0";
    public static final String APP_ROUTE = BASE_ROUTE + "/app/%s";
    static final String FUNCTION_CALL_ROUTE = APP_ROUTE + "/functions/call";
  }
}
