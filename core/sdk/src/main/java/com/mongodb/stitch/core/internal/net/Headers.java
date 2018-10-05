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

import java.util.Locale;

/** HTTP Header definitions and helper methods. */
public final class Headers {
  public static final String CONTENT_TYPE_CANON = "Content-Type";
  public static final String CONTENT_TYPE = CONTENT_TYPE_CANON.toLowerCase(Locale.US);
  private static final String AUTHORIZATION_CANON = "Authorization";
  public static final String AUTHORIZATION = AUTHORIZATION_CANON.toLowerCase(Locale.US);
  private static final String ACCEPT_CANON = "Accept";
  public static final String ACCEPT = ACCEPT_CANON.toLowerCase(Locale.US);
  private static final String AUTHORIZATION_BEARER = "Bearer";

  private Headers() {}

  /**
   * Pulls out the Bearer value (https://tools.ietf.org/html/rfc6750) from an Authorization header.
   *
   * @param value the bearer value
   * @return a standard Authorization Bearer header value.
   */
  public static String getAuthorizationBearer(final String value) {
    return String.format("%s %s", AUTHORIZATION_BEARER, value);
  }
}
