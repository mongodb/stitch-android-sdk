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

package com.mongodb.stitch.core.auth.providers.facebook;

import static com.mongodb.stitch.core.auth.providers.facebook.FacebookAuthProvider.DEFAULT_NAME;
import static com.mongodb.stitch.core.auth.providers.facebook.FacebookAuthProvider.TYPE;

import com.mongodb.stitch.core.auth.ProviderCapabilities;
import com.mongodb.stitch.core.auth.StitchCredential;
import org.bson.Document;

/**
 * The credential used for Facebook OAuth 2.0 log ins.
 */
public final class FacebookCredential implements StitchCredential {

  private final String providerName;
  private final String accessToken;

  /**
   * Constructs a Facebook credential for a user.
   *
   * @param accessToken the access token from Facebook.
   * @see <a href="https://docs.mongodb.com/stitch/auth/facebook-auth/">Facebook Authentication</a>
   */
  public FacebookCredential(final String accessToken) {
    this(DEFAULT_NAME, accessToken);
  }

  private FacebookCredential(final String providerName, final String accessToken) {
    this.providerName = providerName;
    this.accessToken = accessToken;
  }

  @Override
  public String getProviderName() {
    return providerName;
  }

  @Override
  public String getProviderType() {
    return TYPE;
  }

  @Override
  public Document getMaterial() {
    return new Document(Fields.ACCESS_TOKEN, accessToken);
  }

  @Override
  public ProviderCapabilities getProviderCapabilities() {
    return new ProviderCapabilities(false);
  }

  private static class Fields {
    static final String ACCESS_TOKEN = "accessToken";
  }
}
