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
import com.mongodb.stitch.core.internal.net.Transport;

import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;

public class StitchClientConfiguration {
  private final String baseUrl;
  private final Storage storage;
  private final String dataDirectory;
  private final Transport transport;
  private final Long defaultRequestTimeout;
  private final CodecRegistry codecRegistry;

  StitchClientConfiguration(final StitchClientConfiguration config) {
    this.baseUrl = config.baseUrl;
    this.storage = config.storage;
    this.dataDirectory = config.dataDirectory;
    this.transport = config.transport;
    this.defaultRequestTimeout = config.defaultRequestTimeout;
    this.codecRegistry = config.codecRegistry;
  }

  private StitchClientConfiguration(
      final String baseUrl,
      final Storage storage,
      final String dataDirectory,
      final Transport transport,
      final Long defaultRequestTimeout,
      final CodecRegistry codecRegistry) {
    this.baseUrl = baseUrl;
    this.storage = storage;
    this.dataDirectory = dataDirectory;
    this.transport = transport;
    this.defaultRequestTimeout = defaultRequestTimeout;
    this.codecRegistry = codecRegistry;
  }

  /**
   * Gets the base URL of the Stitch server that the client will communicate with.
   */
  public String getBaseUrl() {
    return baseUrl;
  }

  /**
   * Gets the underlying storage for authentication info.
   */
  public Storage getStorage() {
    return storage;
  }

  /**
   * Gets the local directory in which Stitch can store any data (e.g. embedded MongoDB data
   * directory).
   */
  public String getDataDirectory() {
    return dataDirectory;
  }

  /**
   * Gets the {@link Transport} that the client will use to make round trips to the Stitch server.
   */
  public Transport getTransport() {
    return transport;
  }

  /**
   * Gets the number of seconds that a `Transport` should spend by default on an HTTP round trip
   * before failing with an error. Important: If a request timeout was specified for a specific
   * operation, for example in a function call, that timeout will override this one.
   */
  public Long getDefaultRequestTimeout() {
    return defaultRequestTimeout;
  }

  public CodecRegistry getCodecRegistry() {
    return codecRegistry;
  }

  /**
   * Gets the builder form of this configuration.
   */
  public Builder builder() {
    return new Builder(this);
  }

  public static class Builder {
    private String baseUrl;
    private Storage storage;
    private String dataDirectory;
    private Transport transport;
    private Long defaultRequestTimeout;
    private CodecRegistry codecRegistry;

    public Builder() {}

    Builder(final StitchClientConfiguration config) {
      baseUrl = config.baseUrl;
      storage = config.storage;
      dataDirectory = config.dataDirectory;
      transport = config.transport;
      defaultRequestTimeout = config.defaultRequestTimeout;
      codecRegistry = config.codecRegistry;
    }

    /**
     * Sets the base URL of the Stitch server that the client will communicate with.
     */
    public Builder withBaseUrl(final String baseUrl) {
      this.baseUrl = baseUrl;
      return this;
    }

    /**
     * Sets the underlying storage for authentication info.
     */
    public Builder withStorage(final Storage storage) {
      this.storage = storage;
      return this;
    }

    /**
     * Sets the local directory in which Stitch can store any data (e.g. embedded MongoDB data
     * directory).
     */
    public Builder withDataDirectory(final String dataDirectory) {
      this.dataDirectory = dataDirectory;
      return this;
    }

    /**
     * Sets the {@link Transport} that the client will use to make round trips to the Stitch server.
     */
    public Builder withTransport(final Transport transport) {
      this.transport = transport;
      return this;
    }

    /**
     * Sets the number of seconds that a `Transport` should spend by default on an HTTP round trip
     * before failing with an error. Important: If a request timeout was specified for a specific
     * operation, for example in a function call, that timeout will override this one.
     */
    public Builder withDefaultRequestTimeout(final Long defaultRequestTimeout) {
      this.defaultRequestTimeout = defaultRequestTimeout;
      return this;
    }

    /**
     * Merges the provided codec registry with the default codec registry.
     *
     * @param codecRegistry The codec registry to merge with the default registry.
     */
    public Builder withCustomCodecs(final CodecRegistry codecRegistry) {
      this.codecRegistry =
          CodecRegistries.fromRegistries(BsonUtils.DEFAULT_CODEC_REGISTRY, codecRegistry);
      return this;
    }

    /**
     * Gets the base URL of the Stitch server that the client will communicate with.
     */
    public String getBaseUrl() {
      return baseUrl;
    }

    /**
     * Gets the local directory in which Stitch can store any data (e.g. embedded MongoDB data
     * directory).
     */
    public String getDataDirectory() {
      return dataDirectory;
    }

    /**
     * Gets the underlying storage for authentication info.
     */
    public Storage getStorage() {
      return storage;
    }

    /**
     * Gets the {@link Transport} that the client will use to make round trips to the Stitch server.
     */
    public Transport getTransport() {
      return transport;
    }

    /**
     * Gets the number of seconds that a `Transport` should spend by default on an HTTP round trip
     * before failing with an error. Important: If a request timeout was specified for a specific
     * operation, for example in a function call, that timeout will override this one.
     */
    public Long getDefaultRequestTimeout() {
      return defaultRequestTimeout;
    }

    public CodecRegistry getCodecRegistry() {
      return codecRegistry;
    }

    /**
     * Builds the {@link StitchAppClientConfiguration}.
     */
    public StitchClientConfiguration build() {
      if (baseUrl == null || baseUrl.isEmpty()) {
        throw new IllegalArgumentException("baseUrl must be set");
      }

      if (storage == null) {
        throw new IllegalArgumentException("storage must be set");
      }

      if (transport == null) {
        throw new IllegalArgumentException("transport must be set");
      }

      if (defaultRequestTimeout == null) {
        throw new IllegalArgumentException("defaultRequestTimeout must be set");
      }

      return new StitchClientConfiguration(
          baseUrl, storage, dataDirectory, transport, defaultRequestTimeout, codecRegistry);
    }
  }
}
