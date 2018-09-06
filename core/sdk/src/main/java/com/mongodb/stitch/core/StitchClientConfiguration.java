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

import com.mongodb.stitch.core.internal.common.BsonUtils;
import com.mongodb.stitch.core.internal.common.Storage;
import com.mongodb.stitch.core.internal.net.NetworkMonitor;
import com.mongodb.stitch.core.internal.net.Transport;

import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;

/**
 * Properties representing the configuration of a client that communicate with a particular MongoDB
 * Stitch application.
 */
public class StitchClientConfiguration {
  private final String baseUrl;
  private final Storage storage;
  private final String dataDirectory;
  private final Transport transport;
  private final Long defaultRequestTimeout;
  private final CodecRegistry codecRegistry;
  private final NetworkMonitor networkMonitor;

  StitchClientConfiguration(final StitchClientConfiguration config) {
    this.baseUrl = config.baseUrl;
    this.storage = config.storage;
    this.dataDirectory = config.dataDirectory;
    this.transport = config.transport;
    this.defaultRequestTimeout = config.defaultRequestTimeout;
    this.codecRegistry = config.codecRegistry;
    this.networkMonitor = config.networkMonitor;
  }

  private StitchClientConfiguration(
      final String baseUrl,
      final Storage storage,
      final String dataDirectory,
      final Transport transport,
      final Long defaultRequestTimeout,
      final CodecRegistry codecRegistry,
      final NetworkMonitor networkMonitor
  ) {
    this.baseUrl = baseUrl;
    this.storage = storage;
    this.dataDirectory = dataDirectory;
    this.transport = transport;
    this.defaultRequestTimeout = defaultRequestTimeout;
    this.codecRegistry = codecRegistry;
    this.networkMonitor = networkMonitor;
  }

  /**
   * Gets the base URL of the Stitch server that the client will communicate with.
   *
   * @return the base URL of the Stitch server that the client will communicate with.
   */
  public String getBaseUrl() {
    return baseUrl;
  }

  /**
   * Gets the underlying storage to persist client specific data to.
   *
   * @return the underlying storage to persist client specific data to.
   */
  public Storage getStorage() {
    return storage;
  }

  /**
   * Gets the local directory in which Stitch can store any data (e.g. embedded MongoDB data
   * directory).
   *
   * @return the local directory in which Stitch can store any data.
   */
  public String getDataDirectory() {
    return dataDirectory;
  }

  /**
   * Gets the {@link Transport} that the client will use to make round trips to the Stitch server.
   *
   * @return the {@link Transport} that the client will use to make round trips to the Stitch
   *         server.
   */
  public Transport getTransport() {
    return transport;
  }

  /**
   * Gets the number of milliseconds that a `Transport` should spend by default on an HTTP round
   * trip before failing with an error. Important: If a request timeout was specified for a specific
   * operation, for example in a function call, that timeout will override this one.
   *
   * @return the number of milliseconds that a `Transport` should spend by default on an HTTP round
   *         trip before failing with an error.
   */
  public Long getDefaultRequestTimeout() {
    return defaultRequestTimeout;
  }

  /**
   * Gets the codec registry used to encode/decode JSON.
   *
   * @return the codec registry used to encode/decode JSON.
   */
  public CodecRegistry getCodecRegistry() {
    return codecRegistry;
  }

  /**
   * Gets the network monitor.
   *
   * @return the network monitor.
   */
  public NetworkMonitor getNetworkMonitor() {
    return networkMonitor;
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
   * A builder that can build a {@link StitchClientConfiguration }object.
   */
  public static class Builder {
    private String baseUrl;
    private Storage storage;
    private String dataDirectory;
    private Transport transport;
    private Long defaultRequestTimeout;
    private CodecRegistry codecRegistry;
    private NetworkMonitor networkMonitor;

    /**
     * Constructs a new builder.
     */
    public Builder() {}

    Builder(final StitchClientConfiguration config) {
      baseUrl = config.baseUrl;
      storage = config.storage;
      dataDirectory = config.dataDirectory;
      transport = config.transport;
      defaultRequestTimeout = config.defaultRequestTimeout;
      codecRegistry = config.codecRegistry;
      networkMonitor = config.networkMonitor;
    }

    /**
     * Sets the base URL of the Stitch server that the client will communicate with.
     *
     * @param baseUrl the base URL of the Stitch server that the client will communicate with.
     * @return the builder.
     */
    public Builder withBaseUrl(final String baseUrl) {
      this.baseUrl = baseUrl;
      return this;
    }

    /**
     * Sets the underlying storage to persist client specific data to.
     *
     * @param storage the underlying storage to persist client specific data to.
     * @return the builder.
     */
    public Builder withStorage(final Storage storage) {
      this.storage = storage;
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
      this.dataDirectory = dataDirectory;
      return this;
    }

    /**
     * Sets the {@link Transport} that the client will use to make round trips to the Stitch server.
     *
     * @param transport the {@link Transport} that the client will use to make round trips to the
     *                  Stitch server.
     * @return the builder.
     */
    public Builder withTransport(final Transport transport) {
      this.transport = transport;
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
      this.defaultRequestTimeout = defaultRequestTimeout;
      return this;
    }

    /**
     * Merges the provided codec registry with the default codec registry.
     *
     * @param codecRegistry the codec registry to merge with the default registry.
     * @return the builder.
     */
    public Builder withCodecRegistry(final CodecRegistry codecRegistry) {
      // We can't detect if their codecRegistry has any duplicate providers. There's also a chance
      // that putting ours first may prevent decoding of some of their classes if for example they
      // have their own way of decoding an Integer.
      this.codecRegistry =
          CodecRegistries.fromRegistries(BsonUtils.DEFAULT_CODEC_REGISTRY, codecRegistry);
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
      this.networkMonitor = networkMonitor;
      return this;
    }

    /**
     * Gets the base URL of the Stitch server that the client will communicate with.
     *
     * @return the base URL of the Stitch server that the client will communicate with.
     */
    public String getBaseUrl() {
      return baseUrl;
    }

    /**
     * Gets the local directory in which Stitch can store any data (e.g. embedded MongoDB data
     * directory).
     *
     * @return the local directory in which Stitch can store any data.
     */
    public String getDataDirectory() {
      return dataDirectory;
    }

    /**
     * Gets the underlying storage to persist client specific data to.
     *
     * @return the underlying storage to persist client specific data to.
     */
    public Storage getStorage() {
      return storage;
    }

    /**
     * Gets the {@link Transport} that the client will use to make round trips to the Stitch server.
     *
     * @return the {@link Transport} that the client will use to make round trips to the Stitch
     *         server.
     */
    public Transport getTransport() {
      return transport;
    }

    /**
     * Gets the number of milliseconds that a `Transport` should spend by default on an HTTP round
     * trip before failing with an error. Important: If a request timeout was specified for a
     * specific operation, for example in a function call, that timeout will override this one.
     *
     * @return the number of milliseconds that a `Transport` should spend by default on an HTTP
     *         round trip before failing with an error.
     */
    public Long getDefaultRequestTimeout() {
      return defaultRequestTimeout;
    }

    /**
     * Gets the codec registry used to encode/decode JSON.
     *
     * @return the codec registry used to encode/decode JSON.
     */
    public CodecRegistry getCodecRegistry() {
      return codecRegistry;
    }

    /**
     * Gets the {@link NetworkMonitor} that the client will used to check internet status.
     *
     * @return the {@link NetworkMonitor} that the client will used to check internet status.
     */
    public NetworkMonitor getNetworkMonitor() {
      return networkMonitor;
    }

    /**
     * Builds the {@link StitchAppClientConfiguration}.
     *
     * @return the built {@link StitchAppClientConfiguration}.
     */
    public StitchClientConfiguration build() {
      if (codecRegistry == null) {
        codecRegistry = BsonUtils.DEFAULT_CODEC_REGISTRY;
      }

      return new StitchClientConfiguration(
          baseUrl,
          storage,
          dataDirectory,
          transport,
          defaultRequestTimeout,
          codecRegistry,
          networkMonitor);
    }
  }
}
