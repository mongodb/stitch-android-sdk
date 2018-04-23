package com.mongodb.stitch.core.services.internal;

import static com.mongodb.stitch.core.internal.net.StitchAppRoutes.RouteParts.APP_ROUTE;
import static com.mongodb.stitch.core.services.internal.StitchServiceRoutes.RouteParts.FUNCTION_CALL_ROUTE;

public final class StitchServiceRoutes {
  private final String clientAppId;

  public StitchServiceRoutes(final String clientAppId) {
    this.clientAppId = clientAppId;
  }

  public String getFunctionCallRoute() {
    return String.format(FUNCTION_CALL_ROUTE, clientAppId);
  }

  static class RouteParts {
    static final String FUNCTION_CALL_ROUTE = APP_ROUTE + "/functions/call";
  }
}
