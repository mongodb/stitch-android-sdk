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

package com.mongodb.stitch.core;

import com.mongodb.stitch.core.internal.common.Storage;
import com.mongodb.stitch.core.internal.net.NetworkMonitor;
import com.mongodb.stitch.core.internal.net.Transport;
import org.bson.codecs.configuration.CodecRegistry;

/**
 * Properties representing the configuration of an app client that communicate with a particular
 * MongoDB Stitch application.
 */
public final class StitchAppClientConfiguration extends StitchClientConfiguration {
  private final String localAppName;
  private final String localAppVersion;

  private StitchAppClientConfiguration(
      final StitchClientConfiguration config,
      final String localAppName,
      final String localAppVersion) {
    super(config);
    this.localAppVersion = localAppVersion;
    this.localAppName = localAppName;
  }

  /**
   * Gets the name of the local application.
   *
   * @return the name of the local application.
   */
  public String getLocalAppName() {
    return localAppName;
  }

  /**
   * Gets the current version of the local application.
   *
   * @return the current version of the local application.
   */
  public String getLocalAppVersion() {
    return localAppVersion;
  }

  /**
   * Gets the builder form of this configuration.
   *
   * @return the builder form of this configuration.
   */
  public Builder builder() {
    return new Builder(this);
  }

  /**
   * A builder that can build a {@link StitchAppClientConfiguration }object.
   */
  public static class Builder extends StitchClientConfiguration.Builder {
    private String localAppName;
    private String localAppVersion;

    /**
     * Constructs a new builder.
     */
    public Builder() {}

    private Builder(final StitchAppClientConfiguration config) {
      super(config);
      localAppVersion = config.localAppVersion;
      localAppName = config.localAppName;
    }

    /**
     * Sets the name of the local application.
     *
     * @param localAppName the name of the local application.
     * @return the builder.
     */
    public Builder withLocalAppName(final String localAppName) {
      this.localAppName = localAppName;
      return this;
    }

    /**
     * Sets the current version of the local application.
     *
     * @param localAppVersion the current version of the local application.
     * @return the builder.
     */
    public Builder withLocalAppVersion(final String localAppVersion) {
      this.localAppVersion = localAppVersion;
      return this;
    }

    /**
     * Gets the name of the local application.
     *
     * @return the name of the local application.
     */
    public String getLocalAppName() {
      return localAppName;
    }

    /**
     * Gets the current version of the local application.
     *
     * @return the current version of the local application.
     */
    public String getLocalAppVersion() {
      return localAppVersion;
    }

    /**
     * Sets the base URL of the Stitch server that the client will communicate with.
     *
     * @param baseUrl the base URL of the Stitch server that the client will communicate with.
     * @return the builder.
     */
    @Override
    public Builder withBaseUrl(final String baseUrl) {
      super.withBaseUrl(baseUrl);
      return this;
    }

    /**
     * Sets the underlying storage for Stitch to persist client specific data to.
     *
     * @param storage the underlying storage for Stitch to persist client specific data to.
     * @return the builder.
     */
    public Builder withStorage(final Storage storage) {
      super.withStorage(storage);
      return this;
    }

    /**
     * Sets the local directory in which Stitch can store any data (e.g. embedded MongoDB data
     * directory).
     *
     * @param dataDirectory the local directory in which Stitch can store any data.
     * @return the builder.
     */
    public Builder withDataDirectory(final String dataDirectory) {
      super.withDataDirectory(dataDirectory);
      return this;
    }

    /**
     * Sets the {@link Transport} that the client will use to make round trips to the Stitch server.
     *
     * @param transport the {@link Transport} that the client will use to make round trips to
     *                  the Stitch server.
     * @return the builder.
     */
    public Builder withTransport(final Transport transport) {
      super.withTransport(transport);
      return this;
    }

    /**
     * Sets the number of milliseconds that a `Transport` should spend by default on an HTTP round
     * trip before failing with an error. Important: If a request timeout was specified for a
     * specific operation, for example in a function call, that timeout will override this one.
     *
     * @param defaultRequestTimeout the number of milliseconds that a `Transport` should spend by
     *                              default on an HTTP round trip before failing with an error.
     * @return the builder.
     */
    public Builder withDefaultRequestTimeout(final Long defaultRequestTimeout) {
      super.withDefaultRequestTimeout(defaultRequestTimeout);
      return this;
    }

    /**
     * Merges the provided codec registry with the default codec registry.
     *
     * @param codecRegistry the codec registry to merge with the default registry.
     * @return the builder.
     */
    public Builder withCodecRegistry(final CodecRegistry codecRegistry) {
      super.withCodecRegistry(codecRegistry);
      return this;
    }

    /**
     * Sets the {@link NetworkMonitor} that the client will used to check internet status.
     *
     * @param networkMonitor the {@link NetworkMonitor} that the client will use check internet
     *                       status.
     * @return the builder.
     */
    public Builder withNetworkMonitor(final NetworkMonitor networkMonitor) {
      super.withNetworkMonitor(networkMonitor);
      return this;
    }

    /**
     * Builds the {@link StitchAppClientConfiguration}.
     *
     * @return the built {@link StitchAppClientConfiguration}.
     */
    public StitchAppClientConfiguration build() {
      final StitchClientConfiguration config = super.build();
      return new StitchAppClientConfiguration(config, localAppName, localAppVersion);
    }
  }
}
