/*
 * Copyright 2018-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.stitch.core.internal.net;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongodb.stitch.core.internal.common.StitchObjectMapper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Represents the application metadata for a particular Stitch
 * application.
 */
@SuppressWarnings("unused") // Jackson uses reflection
public class AppMetadata {
  String deploymentModel;
  String location;
  String hostname;

  /**
   * Provides the deployment model for the application.
   *
   * @return the deployment model.
   */
  @JsonProperty("deployment_model")
  public String getDeploymentModel() {
    return deploymentModel;
  }

  /**
   * Sets the deployment model for the application.
   *
   * @param deploymentModel the deployment model to set.
   */
  public void setDeploymentModel(final String deploymentModel) {
    this.deploymentModel = deploymentModel;
  }

  /**
   * Provides the location for the application.
   *
   * @return the location.
   */
  @JsonProperty("location")
  public String getLocation() {
    return location;
  }

  /**
   * Sets the application's location.
   *
   * @param location the location to set.
   */
  public void setLocation(final String location) {
    this.location = location;
  }

  /**
   * Provides the hostname to use when accessing client API for the application.
   *
   * @return the client API hostname.
   */
  @JsonProperty("hostname")
  public String getHostname() {
    return hostname;
  }

  /**
   * Sets the client API hostname.
   *
   * @param hostname the hostname to set
   */
  public void setHostname(final String hostname) {
    this.hostname = hostname;
  }

  @Override
  public String toString() {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      StitchObjectMapper.getInstance().writeValue(out, this);
    } catch (Exception e) {
      return "<json serialization error>";
    }

    return new String(out.toByteArray(), StandardCharsets.UTF_8);
  }
}
