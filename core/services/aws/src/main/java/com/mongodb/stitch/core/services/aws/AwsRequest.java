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

package com.mongodb.stitch.core.services.aws;

import org.bson.Document;

/**
 * An AwsRequest encapsulates the details of an AWS request over the AWS service.
 */
public final class AwsRequest {
  private final String service;
  private final String action;
  private final String region;
  private final Document arguments;

  private AwsRequest(
      final String service,
      final String action,
      final String region,
      final Document arguments
  ) {
    this.service = service;
    this.action = action;
    this.region = region;
    this.arguments = arguments;
  }

  /**
   * Returns the AWS service that the action in the request will be performed against.
   *
   * @return the AWS service that the action in the request will be performed against.
   */
  public String getService() {
    return service;
  }

  /**
   * Returns the action within the AWS service to perform.
   *
   * @return the action within the AWS service to perform.
   */
  public String getAction() {
    return action;
  }

  /**
   * Returns the region that service in this request should be scoped to.
   *
   * @return the region that service in this request should be scoped to.
   */
  public String getRegion() {
    return region;
  }

  /**
   * Returns the arguments that will be used in the action.
   *
   * @return the arguments that will be used in the action.
   */
  public Document getArguments() {
    return arguments;
  }

  /**
   * A builder that can build {@link AwsRequest}s.
   */
  public static class Builder {
    private String service;
    private String action;
    private String region;
    private Document arguments;

    /**
     * Constructs a new builder for an AWS request.
     */
    public Builder() {}

    /**
     * Sets the AWS service that the action in the request will be performed against.
     *
     * @param service the AWS service that the action in the request will be performed against.
     * @return the builder.
     */
    public Builder withService(final String service) {
      this.service = service;
      return this;
    }

    /**
     * Sets the action within the AWS service to perform.
     *
     * @param action the action within the AWS service to perform.
     * @return the builder.
     */
    public Builder withAction(final String action) {
      this.action = action;
      return this;
    }

    /**
     * Sets the region that service in this request should be scoped to.
     *
     * @param region the region that service in this request should be scoped to.
     * @return the builder.
     */
    public Builder withRegion(final String region) {
      this.region = region;
      return this;
    }

    /**
     * Sets the arguments that will be used in the action.
     *
     * @param arguments the arguments that will be used in the action.
     * @return the builder.
     */
    public Builder withArguments(final Document arguments) {
      this.arguments = arguments;
      return this;
    }

    /**
     * Builds, validates, and returns the {@link AwsRequest}.
     *
     * @return the built AWS request.
     */
    public AwsRequest build() {
      if (service == null || service.isEmpty()) {
        throw new IllegalArgumentException("must set service");
      }

      if (action == null || action.isEmpty()) {
        throw new IllegalArgumentException("must set action");
      }

      return new AwsRequest(
          service,
          action,
          region,
          arguments == null ? new Document() : arguments);
    }
  }
}
