package com.mongodb.stitch.core.services.mongodb.remote.sync.internal;

import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler;

import org.bson.codecs.Codec;

/**
 * Properties representing the configuration of an app client that communicate with a particular
 * MongoDB Stitch application.
 */
public final class SyncConfiguration {
  private final ConflictHandler<?> conflictHandler;
  private final ChangeEventListener<?> changeEventListener;
  private final ExceptionListener exceptionListener;
  private final SyncFrequency syncFrequency;
  private final Codec<?> codec;


  private SyncConfiguration(
      final ConflictHandler<?> conflictHandler,
      final ChangeEventListener<?> changeEventListener,
      final ExceptionListener exceptionListener,
      final SyncFrequency syncFrequency,
      final Codec<?> codec) {
    this.conflictHandler = conflictHandler;
    this.changeEventListener = changeEventListener;
    this.exceptionListener = exceptionListener;
    this.syncFrequency = syncFrequency;
    this.codec = codec;
  }

  /**
   * Gets the ConflictHandler<T> for the SyncConfiguration
   *
   * @return the ConflictHandler<T> for the SyncConfiguration.
   */
  public ConflictHandler<?> getConflictHandler() {
    return conflictHandler;
  }

  /**
   * Gets the ChangeEventListener<T> for the SyncConfiguration
   *
   * @return the ChangeEventListener<T> for the SyncConfiguration.
   */
  public ChangeEventListener<?> getChangeEventListener() {
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
  public Codec<?> getCodec() {
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
   * A builder that can build a {@link SyncConfiguration} object.
   */
  public static class Builder {
    private ConflictHandler<?> conflictHandler;
    private ChangeEventListener<?> changeEventListener;
    private ExceptionListener exceptionListener;
    private SyncFrequency syncFrequency;
    private Codec<?> codec;

    /**
     * Constructs a new builder.
     */
    public Builder() {}

    /**
     * Constructs a new builder from a SyncConfiguration.
     */
    public Builder(SyncConfiguration syncConfiguration) {
      this.conflictHandler = syncConfiguration.conflictHandler;
      this.changeEventListener = syncConfiguration.changeEventListener;
      this.exceptionListener = syncConfiguration.exceptionListener;
      this.syncFrequency = syncConfiguration.syncFrequency;
      this.codec = syncConfiguration.codec;
    }

    /**
     * Sets the conflictHandler for the SyncConfiguration.
     *
     * @param conflictHandler the ConflictHandler<T> of the SyncConfiguration.
     * @return the builder.
     */
    public Builder withConflictHandler(final ConflictHandler<?> conflictHandler) {
      this.conflictHandler = conflictHandler;
      return this;
    }

    /**
     * Sets the changeEventListener for the SyncConfiguration.
     *
     * @param changeEventListener the ChangeEventListener<T> of the SyncConfiguration.
     * @return the builder.
     */
    public Builder withChangeEventListener(final ChangeEventListener<?> changeEventListener) {
      this.changeEventListener = changeEventListener;
      return this;
    }

    /**
     * Sets the exceptionListener for the SyncConfiguration.
     *
     * @param exceptionListener the ExceptionListener of the SyncConfiguration.
     * @return the builder.
     */
    public Builder withExceptionListener(final ExceptionListener exceptionListener) {
      this.exceptionListener = exceptionListener;
      return this;
    }

    /**
     * Sets the syncFrequency for the SyncConfiguration.
     *
     * @param syncFrequency the SyncFrequency of the SyncConfiguration.
     * @return the builder.
     */
    public Builder withSyncFrequency(final SyncFrequency syncFrequency) {
      this.syncFrequency = syncFrequency;
      return this;
    }

    /**
     * Sets the codec for the SyncConfiguration.
     *
     * @param codec the Codec<T> of the SyncConfiguration.
     * @return the builder.
     */
    public Builder withCodec(final Codec<?> codec) {
      this.codec = codec;
      return this;
    }

    /**
     * Builds the {@link SyncConfiguration}.
     *
     * @return the built {@link SyncConfiguration}.
     */
    public SyncConfiguration build() throws SyncConfigurationException {
      // Must give a conflict handler
      if (conflictHandler == null) {
        throw new SyncConfigurationException("SyncConfiguration cannot have null conflict handler");
      }

      // Defaults to REACTIVE
      if (syncFrequency == null) {
        syncFrequency = SyncFrequency.reactive();
      }

      return new SyncConfiguration(
          conflictHandler,
          changeEventListener,
          exceptionListener,
          syncFrequency,
          codec
      );
    }
  }
}