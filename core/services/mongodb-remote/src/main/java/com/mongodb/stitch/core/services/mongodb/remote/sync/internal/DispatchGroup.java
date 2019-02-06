package com.mongodb.stitch.core.services.mongodb.remote.sync.internal;

/**
 * DispatchGroup allows for aggregate synchronization of work.
 */
class DispatchGroup {
  /**
   * Amount of workers working.
   */
  private int count;
  /**
   * Whether or not the group is blocked from receiving new work.
   */
  private volatile boolean isBlocked;

  DispatchGroup() {
  }

  /**
   * Unblock the group. No-op if already unblocked.
   */
  synchronized void unblock() {
    isBlocked = false;
    notifyAll();
  }

  /**
   * Enter work into the group. If the group is blocked,
   * this method will subsequently be blocking until the
   * group is unblocked.
   */
  synchronized void enter() {
    while (this.isBlocked) {
      try {
        wait();
      } catch (InterruptedException e) {
        // swallow error
      }
    }

    count++;
  }

  /**
   * Exit work from the group. If the group is
   * emptied, it will notify those waiting.
   */
  synchronized void exit() {
    count--;

    if (count == 0) {
      notifyAll();
    }
  }

  /**
   * Block the group and wait for the remaining work to complete.
   */
  void blockAndWait() {
    isBlocked = true;
    while (count > 0) {
      try {
        wait();
      } catch (InterruptedException e) {
        // swallow error
      }
    }
  }
}
