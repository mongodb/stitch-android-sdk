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

import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

/**
 * Base representation of the frequency a user may choose for sync.
 *
 * Configurations should be done using the static initializers for the frequency
 * type of choice, e.g.:
 * <p>
 * <code>
 * sync.configure(SyncFrequency.newScheduledConfiguration(
 *    1, TimeUnit.HOURS, true
 * ));
 * </code>
 * </p>
 */
public abstract class SyncFrequency {
  /**
   * The type of frequency that can be used to schedule sync.
   */
  enum SyncFrequencyType {
    /**
     * When a change is made locally or a change from a remote collection is received,
     * the Stitch application will react to the event immediately.
     * If offline and the change was local, the application will queue the event to
     * be synchronized when back online.
     */
    REACTIVE,
    /**
     * Local/remote events will be queued on the device for
     * a specified amount of time (configurable) before they are applied.
     */
    SCHEDULED,
    /**
     * The collection will only sync changes when
     * specified by the application.
     */
    ON_DEMAND
  }

  /**
   * When a change is made locally or a change from a remote collection is received,
   * the Stitch application will react to the event immediately.
   * If offline and the change was local, the application will queue the event to
   * be synchronized when back online.
   */
  static class Reactive extends SyncFrequency {
    static final Reactive instance = new Reactive();

    private Reactive() {
    }

    @Override
    SyncFrequencyType getType() {
      return SyncFrequencyType.REACTIVE;
    }
  }

  /**
   * Local/remote events will be queued on the device for
   * a specified amount of time (configurable) before they are applied.
   */
  static class Scheduled extends SyncFrequency {
    private final long timeInterval;
    private final TimeUnit timeUnit;
    private final boolean isConnected;

    Scheduled(final long timeInterval,
              final @Nonnull TimeUnit timeUnit,
              final boolean isConnected) {
      this.timeInterval = timeInterval;
      this.timeUnit = timeUnit;
      this.isConnected = isConnected;
    }

    @Override
    SyncFrequencyType getType() {
      return SyncFrequencyType.SCHEDULED;
    }

    /**
     * Whether or not the application continuously applying events by maintaining a sync stream
     * @return true if continuously applying events; false if not
     */
    boolean isConnected() {
      return isConnected;
    }

    /**
     * The amount of time in the given {@link TimeUnit} between syncs.
     * A thread will be scheduled to run at the associated interval.
     * @return the interval
     */
    long getTimeInterval() {
      return timeInterval;
    }

    /**
     * The {@link TimeUnit} qualifier for the timeInterval number.
     * @return the associated {@link TimeUnit}
     */
    TimeUnit getTimeUnit() {
      return timeUnit;
    }
  }

  /**
   * The collection will only sync changes when
   * specified by the application.
   */
  static class OnDemand extends SyncFrequency {
    static final OnDemand instance = new OnDemand();

    private OnDemand() {
    }

    @Override
    SyncFrequencyType getType() {
      return SyncFrequencyType.ON_DEMAND;
    }
  }

  /**
   * A configuration to enforce that when a change is made locally or a change
   * from a remote collection is received, the Stitch application
   * will react to the event immediately. If offline and the change was local,
   * the application will queue the event to be synchronized when back online.
   * @return a reactive configuration
   */
  public static SyncFrequency reactive() {
    return Reactive.instance;
  }

  /**
   * A configuration to enforce that local/remote events will be queued on the device for
   * a specified amount of time (configurable) before they are applied.
   * @param timeInterval The amount of time in the given {@link TimeUnit} between syncs.
   * @param timeUnit The {@link TimeUnit} qualifier for the timeInterval number.
   * @param isConnected Whether or not the application continuously
   *                    applying events by maintaining a sync stream
   * @return a scheduled configuration
   */
  public static SyncFrequency scheduled(final long timeInterval,
                                        @Nonnull final TimeUnit timeUnit,
                                        final boolean isConnected) {
    return new Scheduled(timeInterval, timeUnit, isConnected);
  }

  /**
   * A configuration to enforce that the collection will only sync changes when
   * specified by the application.
   * @return an on-demand configuration
   */
  public static SyncFrequency onDemand() {
    return OnDemand.instance;
  }

  abstract SyncFrequencyType getType();
}
