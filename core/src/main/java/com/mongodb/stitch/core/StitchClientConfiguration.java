package com.mongodb.stitch.core;

import com.mongodb.stitch.core.internal.common.BSONUtils;
import com.mongodb.stitch.core.internal.common.Storage;
import com.mongodb.stitch.core.internal.net.Transport;

import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;

public class StitchClientConfiguration {
  private final String baseURL;
  private final Storage storage;
  private final String dataDirectory;
  private final Transport transport;
  private final CodecRegistry codecRegistry;

  StitchClientConfiguration(final StitchClientConfiguration config) {
    this.baseURL = config.baseURL;
    this.storage = config.storage;
    this.dataDirectory = config.dataDirectory;
    this.transport = config.transport;
    this.codecRegistry = config.codecRegistry;
  }

  private StitchClientConfiguration(
      final String baseURL,
      final Storage storage,
      final String dataDirectory,
      final Transport transport,
      final CodecRegistry codecRegistry) {
    this.baseURL = baseURL;
    this.storage = storage;
    this.dataDirectory = dataDirectory;
    this.transport = transport;
    this.codecRegistry = codecRegistry;
  }

  public Builder builder() {
    return new Builder(this);
  }

  public String getBaseURL() {
    return baseURL;
  }

  public Storage getStorage() {
    return storage;
  }

  public String getDataDirectory() {
    return dataDirectory;
  }

  public Transport getTransport() {
    return transport;
  }

  public CodecRegistry getCodecRegistry() { return codecRegistry; }

  public static class Builder {
    private String baseURL;
    private Storage storage;
    private String dataDirectory;
    private Transport transport;
    private CodecRegistry codecRegistry;

    public Builder() {}

    Builder(final StitchClientConfiguration config) {
      baseURL = config.baseURL;
      storage = config.storage;
      dataDirectory = config.dataDirectory;
      transport = config.transport;
      codecRegistry = config.codecRegistry;
    }

    @SuppressWarnings("UnusedReturnValue")
    public Builder withBaseURL(final String baseURL) {
      this.baseURL = baseURL;
      return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    public Builder withStorage(final Storage storage) {
      this.storage = storage;
      return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    public Builder withDataDirectory(final String dataDirectory) {
      this.dataDirectory = dataDirectory;
      return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    public Builder withTransport(final Transport transport) {
      this.transport = transport;
      return this;
    }

    /**
     * Merges the provided codec registry with the default codec registry.
     * @param codecRegistry The codec registry to merge with the default registry.
     */
    @SuppressWarnings("UnusedReturnValue")
    public Builder withCustomCodecs(final CodecRegistry codecRegistry) {
      this.codecRegistry = CodecRegistries.fromRegistries(
              BSONUtils.DEFAULT_CODEC_REGISTRY,
              codecRegistry
      );
      return this;
    }

    public String getBaseURL() {
      return baseURL;
    }

    public String getDataDirectory() {
      return dataDirectory;
    }

    public Storage getStorage() {
      return storage;
    }

    public Transport getTransport() {
      return transport;
    }

    public CodecRegistry getCodecRegistry() { return codecRegistry; }

    public StitchClientConfiguration build() {
      if (baseURL == null || baseURL.isEmpty()) {
        throw new IllegalArgumentException("baseURL must be set");
      }

      if (storage == null) {
        throw new IllegalArgumentException("storage must be set");
      }

      if (transport == null) {
        throw new IllegalArgumentException("transport must be set");
      }

      return new StitchClientConfiguration(baseURL, storage, dataDirectory, transport, codecRegistry);
    }
  }
}
