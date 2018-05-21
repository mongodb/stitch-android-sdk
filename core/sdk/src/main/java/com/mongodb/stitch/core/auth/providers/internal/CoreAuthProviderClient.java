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

package com.mongodb.stitch.core.auth.providers.internal;

/**
 * The class from which all Core auth provider clients inherit. Only auth provider clients that
 * make requests to the Stitch server need to inherit this class.
 */
public abstract class CoreAuthProviderClient<RequestClientT> {
  private final String providerName;
  private final RequestClientT requestClient;
  private final String baseRoute;

  /**
   * A basic constructor, which sets the provider client's properties to the values provided in
   * the parameters.
   */
  protected CoreAuthProviderClient(
          final String providerName,
          final RequestClientT requestClient,
          final String baseRoute) {
    this.providerName = providerName;
    this.requestClient = requestClient;
    this.baseRoute = baseRoute;
  }

  /**
   * Returns the name of the authentication provider.
   */
  protected String getProviderName() {
    return providerName;
  }

  /**
   * Returns the request client used by the client to make requests. Is generic since some auth
   * provider clients use an authenticated request client while others use an unauthenticated
   * request client.
   */
  protected RequestClientT getRequestClient() {
    return requestClient;
  }

  /**
   * Returns the base route for this authentication provider client.
   */
  protected String getBaseRoute() {
    return baseRoute;
  }
}
