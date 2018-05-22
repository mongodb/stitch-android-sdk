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

import org.bson.Document;

/**
 * A StitchCredential provides a Stitch client the information needed to log in or link a user with
 * an identity.
 */
public interface StitchCredential {

  /**
   * Returns the authentication provider name that this credential is for. (e.g. local-userpass
   * for the User/Password authentication provider)
   *
   * @return the authentication provider name that this credential is for.
   */
  String getProviderName();

  /**
   * Returns the authentication provider type that this credential is for. (e.g. local-userpass
   * for the User/Password authentication provider)
   *
   * @return the authentication provider type that this credential is for.
   */
  String getProviderType();

  /**
   * Returns the authentication material for this credential. This is the authentication provider
   * specific information if it's necessary. For example, this could be the username and password
   * of an identity when using the User/Password authentication provider.
   *
   * @return the authentication material for this credential.
   */
  Document getMaterial();

  /**
   * Returns the provider capabilities associated with this credential.
   *
   * @return the provider capabilities associated with this credential.
   */
  ProviderCapabilities getProviderCapabilities();
}
