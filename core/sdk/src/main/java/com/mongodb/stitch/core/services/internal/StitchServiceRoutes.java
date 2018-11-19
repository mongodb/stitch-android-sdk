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

package com.mongodb.stitch.core.services.internal;

import static com.mongodb.stitch.core.internal.net.StitchAppRoutes.RouteParts.APP_ROUTE;
import static com.mongodb.stitch.core.services.internal.StitchServiceRoutes.RouteParts.FUNCTION_CALL_ROUTE;
import static com.mongodb.stitch.core.services.internal.StitchServiceRoutes.RouteParts.LOCATION_ROUTE;

public final class StitchServiceRoutes {
  private final String clientAppId;

  public StitchServiceRoutes(final String clientAppId) {
    this.clientAppId = clientAppId;
  }

  public String getFunctionCallRoute() {
    return String.format(FUNCTION_CALL_ROUTE, clientAppId);
  }

  public String getLocationRoute() {
    return String.format(LOCATION_ROUTE, clientAppId);
  }

  static class RouteParts {
    static final String FUNCTION_CALL_ROUTE = APP_ROUTE + "/functions/call";
    static final String LOCATION_ROUTE = APP_ROUTE + "/location";
  }
}
