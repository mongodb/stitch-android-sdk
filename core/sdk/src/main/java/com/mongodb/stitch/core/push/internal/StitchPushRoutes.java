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

package com.mongodb.stitch.core.push.internal;

import static com.mongodb.stitch.core.internal.net.StitchAppRoutes.RouteParts.APP_ROUTE;
import static com.mongodb.stitch.core.push.internal.StitchPushRoutes.RouteParts.REGISTRATION_ROUTE;

public final class StitchPushRoutes {
  private final String clientAppId;

  public StitchPushRoutes(final String clientAppId) {
    this.clientAppId = clientAppId;
  }

  public String getRegistrationRoute(final String serviceName) {
    return String.format(REGISTRATION_ROUTE, clientAppId, serviceName);
  }

  public static class RouteParts {
    static final String BASE_PUSH_ROUTE = APP_ROUTE + "/push";
    static final String PUSH_PROVIDERS_ROUTE = BASE_PUSH_ROUTE + "/providers";
    static final String REGISTRATION_ROUTE = PUSH_PROVIDERS_ROUTE + "/%s/registration";
  }
}
