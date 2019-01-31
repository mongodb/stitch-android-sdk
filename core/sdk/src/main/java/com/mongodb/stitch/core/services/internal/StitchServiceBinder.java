package com.mongodb.stitch.core.services.internal;

/**
 * An ifc that allows any service of any type
 * to bind to it's associated {@link CoreStitchServiceClient}.
 *
 * {@link CoreStitchServiceClient#bind(StitchServiceBinder)}
 */
public interface StitchServiceBinder {
  /**
   * Notify the binder that a rebind event has occured.
   * E.g., a change in authentication.
   */
  void onRebindEvent();
}
