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

  /**
   * Constructs a new fully specified cookie.
   *
   * @param name the name of the cookie.
   * @param value the value of the cookie.
   * @param path the path within the domain to which this cookie belongs.
   * @param domain the domain to which this cookie belongs.
   * @param expires when the cookie expires.
   * @param maxAge how long the cookie can live for.
   * @param secure whether or not this cookie can only be sent to HTTPS servers.
   * @param httpOnly whether or not this cookie can only be read by a server.
   */
  public HttpCookie(
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

  /**
   * Returns the name of the cookie.
   *
   * @return the name of the cookie.
   */
  @Nonnull
  public String getName() {
    return name;
  }

  /**
   * Returns the value of the cookie.
   *
   * @return the value of the cookie.
   */
  @Nonnull
  public String getValue() {
    return value;
  }

  /**
   * Returns the path within the domain to which this cookie belongs.
   *
   * @return the path within the domain to which this cookie belongs.
   */
  @Nullable
  public String getPath() {
    return path;
  }

  /**
   * Returns the domain to which this cookie belongs.
   *
   * @return the domain to which this cookie belongs.
   */
  @Nullable
  public String getDomain() {
    return domain;
  }

  /**
   * Returns when the cookie expires.
   *
   * @return when the cookie expires.
   */
  @Nullable
  public String getExpires() {
    return expires;
  }

  /**
   * Returns how long the cookie can live for.
   *
   * @return how long the cookie can live for.
   */
  @Nullable
  public Integer getMaxAge() {
    return maxAge;
  }

  /**
   * Returns whether or not this cookie can only be sent to HTTPS servers.
   *
   * @return whether or not this cookie can only be sent to HTTPS servers.
   */
  @Nullable
  public Boolean isSecure() {
    return secure;
  }

  /**
   * Returns whether or not this cookie can only be read by a server.
   *
   * @return whether or not this cookie can only be read by a server.
   */
  @Nullable
  public Boolean isHttpOnly() {
    return httpOnly;
  }
}
