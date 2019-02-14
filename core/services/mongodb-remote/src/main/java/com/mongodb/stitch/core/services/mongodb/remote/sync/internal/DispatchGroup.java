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
  synchronized void blockAndWait() {
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
