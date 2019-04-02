package com.mongodb.stitch.core.services.mongodb.remote.sync.internal;

import java.util.concurrent.TimeUnit;

enum SyncCadenceType {
  REACTIVE,
  SCHEDULED,
  ON_DEMAND
}

public abstract class SyncCadence {
  public static SyncCadence newReactiveCadence() {
    return new ReactiveCadence();
  }

  public static SyncCadence newScheduledCadence(long timeInterval,
                                                TimeUnit timeUnit,
                                                boolean isConnected) {
    return new ScheduledCadence(timeInterval, timeUnit, isConnected);
  }

  public static SyncCadence newOnDemandCadence() {
    return new OnDemandCadence();
  }

  abstract SyncCadenceType getType();
}

class ReactiveCadence extends SyncCadence {
  @Override
  SyncCadenceType getType() {
    return SyncCadenceType.REACTIVE;
  }
}


class ScheduledCadence extends SyncCadence {
  private long timeInterval;
  private TimeUnit timeUnit;
  private boolean isConnected;

  ScheduledCadence(long timeInterval,
                   TimeUnit timeUnit,
                   boolean isConnected) {
    this.timeInterval = timeInterval;
    this.timeUnit = timeUnit;
    this.isConnected = isConnected;
  }

  @Override
  SyncCadenceType getType() {
    return SyncCadenceType.SCHEDULED;
  }

  boolean isConnected() {
    return isConnected;
  }

  long getTimeInterval() {
    return timeInterval;
  }

  TimeUnit getTimeUnit() {
    return timeUnit;
  }
}

class OnDemandCadence extends SyncCadence {
  @Override
  SyncCadenceType getType() {
    return SyncCadenceType.ON_DEMAND;
  }
}