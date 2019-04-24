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
   * Gets the ConflictHandler for the SyncConfiguration
   *
   * @return the ConflictHandler for the SyncConfiguration.
   */
  public ConflictHandler<?> getConflictHandler() {
    return conflictHandler;
  }

  /**
   * Gets the ChangeEventListener for the SyncConfiguration
   *
   * @return the ChangeEventListener for the SyncConfiguration.
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
   * Gets the Codec for the SyncConfiguration
   *
   * @return the Codec for the SyncConfiguration.
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
    public Builder(final SyncConfiguration syncConfiguration) {
      this.conflictHandler = syncConfiguration.conflictHandler;
      this.changeEventListener = syncConfiguration.changeEventListener;
      this.exceptionListener = syncConfiguration.exceptionListener;
      this.syncFrequency = syncConfiguration.syncFrequency;
      this.codec = syncConfiguration.codec;
    }

    /**
     * Sets the conflictHandler for the SyncConfiguration.
     *
     * @param conflictHandler the ConflictHandler of the SyncConfiguration.
     * @return the builder.
     */
    public Builder withConflictHandler(final ConflictHandler<?> conflictHandler) {
      this.conflictHandler = conflictHandler;
      return this;
    }

    /**
     * Sets the changeEventListener for the SyncConfiguration.
     *
     * @param changeEventListener the ChangeEventListener of the SyncConfiguration.
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
     * @param codec the Codec of the SyncConfiguration.
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