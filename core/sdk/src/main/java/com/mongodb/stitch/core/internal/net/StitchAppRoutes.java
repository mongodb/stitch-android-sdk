/*
 * Copyright 2018-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.stitch.core.internal.net;

import com.mongodb.stitch.core.services.internal.StitchServiceRoutes;

public final class StitchAppRoutes {
  private final String clientAppId;
  private final StitchAppAuthRoutes authRoutes;
  private final StitchServiceRoutes serviceRoutes;

  /**
   * Constructs the app specific routes to access an app based on the client app id.
   *
   * @param clientAppId the client app id of the app that will be used to communicate with Stitch.
   */
  public StitchAppRoutes(final String clientAppId) {
    this.clientAppId = clientAppId;
    authRoutes = new StitchAppAuthRoutes(clientAppId);
    serviceRoutes = new StitchServiceRoutes(clientAppId);
  }

  /**
   * Returns the auth routes for this app.
   *
   * @return the auth routes for this app.
   */
  public StitchAppAuthRoutes getAuthRoutes() {
    return authRoutes;
  }

  /**
   * Returns the service routes for this app.
   *
   * @return the service routes for this app.
   */
  public StitchServiceRoutes getServiceRoutes() {
    return serviceRoutes;
  }

  /**
   * Returns the function call route for this app.
   *
   * @return the function call route for this app.
   */
  public String getFunctionCallRoute() {
    return String.format(RouteParts.FUNCTION_CALL_ROUTE, clientAppId);
  }

  public static class RouteParts {
    static final String BASE_ROUTE = "/api/client/v2.0";
    public static final String APP_ROUTE = BASE_ROUTE + "/app/%s";
    static final String FUNCTION_CALL_ROUTE = APP_ROUTE + "/functions/call";
  }
}
