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

package com.mongodb.stitch.core.services.http;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents a RFC 6265 cookie.
 */
public class HttpCookie {
  private final String name;
  private final String value;
  private final String path;
  private final String domain;
  private final String expires;
  private final Integer maxAge;
  private final Boolean secure;
  private final Boolean httpOnly;

  HttpCookie(
      @Nonnull final String name,
      @Nonnull final String value,
      @Nullable final String path,
      @Nullable final String domain,
      @Nullable final String expires,
      @Nullable final Integer maxAge,
      @Nullable final Boolean secure,
      @Nullable final Boolean httpOnly
  ) {
    this.name = name;
    this.value = value;
    this.path = path;
    this.domain = domain;
    this.expires = expires;
    this.maxAge = maxAge;
    this.secure = secure;
    this.httpOnly = httpOnly;
  }

  @Nonnull
  public String getName() {
    return name;
  }

  @Nonnull
  public String getValue() {
    return value;
  }

  @Nullable
  public String getPath() {
    return path;
  }

  @Nullable
  public String getDomain() {
    return domain;
  }

  @Nullable
  public String getExpires() {
    return expires;
  }

  @Nullable
  public Integer getMaxAge() {
    return maxAge;
  }

  @Nullable
  public Boolean isSecure() {
    return secure;
  }

  @Nullable
  public Boolean isHttpOnly() {
    return httpOnly;
  }
}
