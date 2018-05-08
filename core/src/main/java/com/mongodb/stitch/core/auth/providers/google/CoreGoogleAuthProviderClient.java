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

package com.mongodb.stitch.core.auth.providers.google;

public abstract class CoreGoogleAuthProviderClient {

  public static final String DEFAULT_PROVIDER_NAME = "oauth2-google";
  static final String PROVIDER_TYPE = "oauth2-google";
  private final String providerName;

  protected CoreGoogleAuthProviderClient(final String providerName) {
    this.providerName = providerName;
  }

  public GoogleCredential getCredential(final String authCode) {
    return new GoogleCredential(providerName, authCode);
  }
}
