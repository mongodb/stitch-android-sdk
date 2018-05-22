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

package com.mongodb.stitch.core.auth;

/**
 * An identity that belongs to a Stitch user.
 */
public class StitchUserIdentity {
  private final String id;
  private final String providerType;

  /**
   * Constructs a new identity with the given id and provider type.
   *
   * @param id the id of the identity.
   * @param providerType the type of the provider.
   */
  public StitchUserIdentity(final String id, final String providerType) {
    this.id = id;
    this.providerType = providerType;
  }

  protected StitchUserIdentity(final StitchUserIdentity identity) {
    this.id = identity.id;
    this.providerType = identity.providerType;
  }

  /**
   * Returns the id of the identity. This is generally an opaque value that shouldn't be used.
   *
   * @return the id of the identity.
   */
  public String getId() {
    return id;
  }

  /**
   * Returns the type of the provider that this identity is for. A user may be linked to multiple
   * identities of the same provider type. This value is useful to check if a user has not
   * registered with a certain provider yet.
   *
   * @return the type of the provider that this identity is for.
   */
  public String getProviderType() {
    return providerType;
  }
}
