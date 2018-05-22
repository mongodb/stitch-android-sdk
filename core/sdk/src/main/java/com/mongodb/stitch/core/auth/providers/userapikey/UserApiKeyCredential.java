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

package com.mongodb.stitch.core.auth.providers.userapikey;

import static com.mongodb.stitch.core.auth.providers.userapikey.UserApiKeyAuthProvider.DEFAULT_NAME;
import static com.mongodb.stitch.core.auth.providers.userapikey.UserApiKeyAuthProvider.TYPE;

import com.mongodb.stitch.core.auth.ProviderCapabilities;
import com.mongodb.stitch.core.auth.StitchCredential;
import org.bson.Document;

/**
 * The credential used for user API key log ins.
 */
public final class UserApiKeyCredential implements StitchCredential {

  private final String providerName;
  private final String key;

  /**
   * Constructs a user API key credential for a user.
   *
   * @param key the API key.
   */
  public UserApiKeyCredential(final String key) {
    this(DEFAULT_NAME, key);
  }

  private UserApiKeyCredential(final String providerName, final String key) {
    this.providerName = providerName;
    this.key = key;
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
    return new Document(Fields.KEY, key);
  }

  @Override
  public ProviderCapabilities getProviderCapabilities() {
    return new ProviderCapabilities(false);
  }

  private static class Fields {
    static final String KEY = "key";
  }
}
