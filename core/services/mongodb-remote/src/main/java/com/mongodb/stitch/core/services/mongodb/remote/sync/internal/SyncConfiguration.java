package com.mongodb.stitch.core.services.mongodb.remote.sync.internal;

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.StitchAppClientConfiguration.Builder;
import com.mongodb.stitch.core.StitchClientConfiguration;
import com.mongodb.stitch.core.internal.common.Storage;
import com.mongodb.stitch.core.internal.net.NetworkMonitor;
import com.mongodb.stitch.core.internal.net.Transport;
import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler;
import com.sun.corba.se.impl.orbutil.concurrent.Sync;

import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Properties representing the configuration of an app client that communicate with a particular
 * MongoDB Stitch application.
 */
public final class SyncConfiguration<T> {
  private final ChangeEventListener<T> changeEventListener;
  private final ExceptionListener exceptionListener;
  private final SyncFrequency syncFrequency;
  private final Codec<T> codec;


  private SyncConfiguration(
      final ChangeEventListener<T> changeEventListener,
      final ExceptionListener exceptionListener,
      final SyncFrequency syncFrequency,
      final Codec<T> codec) {
    this.changeEventListener = changeEventListener;
    this.exceptionListener = exceptionListener;
    this.syncFrequency = syncFrequency;
    this.codec = codec;
  }

  /**
   * Gets the ChangeEventListener<T> for the SyncConfiguration
   *
   * @return the ChangeEventListener<T> for the SyncConfiguration.
   */
  public ChangeEventListener<T> getChangeEventListener() {
    return changeEventListener;
  }

  /**
   * Gets the ExceptionListener for the SyncConfiguration
   *
   * @return the ExceptionListener for the SyncConfiguration.
   */
  public ExceptionListener getExceptionListener() {
    return exceptionListener;
  }

  /**
   * Gets the SyncFrequency for the SyncConfiguration
   *
   * @return the SyncFrequency for the SyncConfiguration.
   */
  public SyncFrequency getSyncFrequency() {
    return syncFrequency;
  }

  /**
   * Gets the Codec<T> for the SyncConfiguration
   *
   * @return the Codec<T> for the SyncConfiguration.
   */
  public Codec<T> getCodec() {
    return codec;
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
   * A builder that can build a {@link com.mongodb.stitch.core.StitchAppClientConfiguration }object.
   */
  public static class Builder {
    private String localAppName;
    private String localAppVersion;

    /**
     * Constructs a new builder.
     */
    public Builder() {}

    private Builder(final com.mongodb.stitch.core.StitchAppClientConfiguration config) {
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
    public com.mongodb.stitch.core.StitchAppClientConfiguration.Builder withLocalAppName(final String localAppName) {
      this.localAppName = localAppName;
      return this;
    }

    /**
     * Sets the current version of the local application.
     *
     * @param localAppVersion the current version of the local application.
     * @return the builder.
     */
    public com.mongodb.stitch.core.StitchAppClientConfiguration.Builder withLocalAppVersion(final String localAppVersion) {
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
     * Sets the base URL of the Stitch server that the client will communicate with to retrieve
     * application metadata.
     *
     * @param baseUrl the base URL of the Stitch server that the client will communicate with.
     * @return the builder.
     */
    @Override
    public com.mongodb.stitch.core.StitchAppClientConfiguration.Builder withBaseUrl(final String baseUrl) {
      super.withBaseUrl(baseUrl);
      return this;
    }

    /**
     * Sets the underlying storage for Stitch to persist client specific data to.
     *
     * @param storage the underlying storage for Stitch to persist client specific data to.
     * @return the builder.
     */
    public com.mongodb.stitch.core.StitchAppClientConfiguration.Builder withStorage(final Storage storage) {
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
    public com.mongodb.stitch.core.StitchAppClientConfiguration.Builder withDataDirectory(final String dataDirectory) {
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
    public com.mongodb.stitch.core.StitchAppClientConfiguration.Builder withTransport(final Transport transport) {
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
    public com.mongodb.stitch.core.StitchAppClientConfiguration.Builder withDefaultRequestTimeout(final Long defaultRequestTimeout) {
      super.withDefaultRequestTimeout(defaultRequestTimeout);
      return this;
    }

    /**
     * Merges the provided codec registry with the default codec registry.
     *
     * @param codecRegistry the codec registry to merge with the default registry.
     * @return the builder.
     */
    public com.mongodb.stitch.core.StitchAppClientConfiguration.Builder withCodecRegistry(final CodecRegistry codecRegistry) {
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
    public com.mongodb.stitch.core.StitchAppClientConfiguration.Builder withNetworkMonitor(final NetworkMonitor networkMonitor) {
      super.withNetworkMonitor(networkMonitor);
      return this;
    }

    /**
     * Builds the {@link com.mongodb.stitch.core.StitchAppClientConfiguration}.
     *
     * @return the built {@link com.mongodb.stitch.core.StitchAppClientConfiguration}.
     */
    public com.mongodb.stitch.core.StitchAppClientConfiguration build() {
      final StitchClientConfiguration config = super.build();
      return new com.mongodb.stitch.core.StitchAppClientConfiguration(config, localAppName, localAppVersion);
    }
  }
}


public class SyncConfiguration {
}
