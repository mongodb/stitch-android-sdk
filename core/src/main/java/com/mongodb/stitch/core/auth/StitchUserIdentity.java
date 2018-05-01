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

public class StitchUserIdentity {
  private final String id;
  private final String providerType;

  public StitchUserIdentity(final String id, final String providerType) {
    this.id = id;
    this.providerType = providerType;
  }

  protected StitchUserIdentity(final StitchUserIdentity identity) {
    this.id = identity.id;
    this.providerType = identity.providerType;
  }

  public String getId() {
    return id;
  }

  public String getProviderType() {
    return providerType;
  }
}
