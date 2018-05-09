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

import java.util.Map;

/**
 * A {@link StitchRequest} that is authenticated by a logged in user.
 */
public class StitchAuthRequest extends StitchRequest {
  private final boolean useRefreshToken;
  private final boolean shouldRefreshOnFailure;

  /**
   * Constructs a request from an existing authenticated request.
   */
  StitchAuthRequest(final StitchAuthRequest request) {
    this(request, request.useRefreshToken, request.shouldRefreshOnFailure);
  }

  /**
   * Upgrades a request to an authenticated request.
   *
   * @param request The normal, unauthenticated request to upgrade.
   * @param useRefreshToken Whether or not to use a refresh token in this request.
   */
  StitchAuthRequest(final StitchRequest request, final boolean useRefreshToken) {
    this(request, useRefreshToken, true);
  }

  /**
   * Upgrades a request to an authenticated request.
   *
   * @param request The normal, unauthenticated request to upgrade.
   * @param useRefreshToken Whether or not to use a refresh token in this request.
   * @param shouldRefreshOnFailure Whether or not the performer of this request should attempt to
   *     refresh authentication info on failure.
   */
  StitchAuthRequest(
      final StitchRequest request,
      final boolean useRefreshToken,
      final boolean shouldRefreshOnFailure) {
    super(
        request.getMethod(),
        request.getPath(),
        request.getTimeout(),
        request.getHeaders(),
        request.getBody(),
        request.getStartedAt());
    this.useRefreshToken = useRefreshToken;
    this.shouldRefreshOnFailure = shouldRefreshOnFailure;
  }

  /**
   * Returns whether or not a refresh token should be used in this request.
   */
  public boolean getUseRefreshToken() {
    return useRefreshToken;
  }

  /**
   * Returns whether or not the performer of this request should attempt to refresh authentication
   * info on failure.
   */
  public boolean getShouldRefreshOnFailure() {
    return shouldRefreshOnFailure;
  }

  /**
   * Returns a copy of this request in builder form.
   */
  public Builder builder() {
    return new Builder(this);
  }

  /**
   * A builder that can build {@link StitchAuthRequest}s.
   */
  public static class Builder extends StitchRequest.Builder {
    private boolean useRefreshToken;
    private boolean shouldRefreshOnFailure = true;

    public Builder() {
      super();
    }

    Builder(final StitchAuthRequest request) {
      super(request);
      useRefreshToken = request.useRefreshToken;
    }

    /**
     * Set if this request should use an access token in this request.
     */
    public Builder withAccessToken() {
      this.useRefreshToken = false;
      return this;
    }

    /**
     * Set if this request should use a refresh token in this request.
     */
    public Builder withRefreshToken() {
      this.useRefreshToken = true;
      return this;
    }

    /**
     * Sets whether or not the performer of this request should attempt to refresh authentication
     * info on failure.
     */
    public Builder withShouldRefreshOnFailure(final boolean shouldRefresh) {
      this.shouldRefreshOnFailure = shouldRefresh;
      return this;
    }

    /**
     * Sets the HTTP method of the request.
     */
    public Builder withMethod(final Method method) {
      super.withMethod(method);
      return this;
    }

    /**
     * Sets the Stitch API path of the request.
     */
    public Builder withPath(final String path) {
      super.withPath(path);
      return this;
    }

    /**
     * Sets the headers that will be included in the request.
     */
    public Builder withHeaders(final Map<String, String> headers) {
      super.withHeaders(headers);
      return this;
    }

    /**
     * Sets a copy of the body that will be sent along with the request.
     */
    public Builder withBody(final byte[] body) {
      super.withBody(body);
      return this;
    }

    /**
     * Builds the request.
     */
    public StitchAuthRequest build() {
      if (useRefreshToken) {
        shouldRefreshOnFailure = true;
      }
      return new StitchAuthRequest(super.build(), useRefreshToken, shouldRefreshOnFailure);
    }
  }

  @Override
  public boolean equals(final Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof StitchAuthRequest)) {
      return false;
    }
    final StitchAuthRequest other = (StitchAuthRequest) object;
    return super.equals(other)
        && getUseRefreshToken() == other.getUseRefreshToken()
        && getShouldRefreshOnFailure() == other.getShouldRefreshOnFailure();
  }

  @Override
  public int hashCode() {
    return super.hashCode()
        + Boolean.valueOf(getUseRefreshToken()).hashCode()
        + Boolean.valueOf(getShouldRefreshOnFailure()).hashCode();
  }
}
