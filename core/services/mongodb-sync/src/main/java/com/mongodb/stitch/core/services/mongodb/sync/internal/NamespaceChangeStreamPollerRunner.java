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

package com.mongodb.stitch.core.services.mongodb.sync.internal;

import com.mongodb.stitch.core.internal.net.NetworkMonitor;

import java.lang.ref.WeakReference;
import org.bson.diagnostics.Logger;

/**
 * This runner runs {@link DataSynchronizer#doSyncPass()} on a periodic interval.
 */
class NamespaceChangeStreamPollerRunner implements Runnable, NetworkMonitor.StateListener {

  private static final Long SHORT_SLEEP_MILLIS = 500L;
  private static final Long LONG_SLEEP_MILLIS = 5000L;

  private final WeakReference<NamespaceChangeStreamShortPoller> pollerRef;
  private final NetworkMonitor networkMonitor;
  private final Logger logger;

  public NamespaceChangeStreamPollerRunner(
      final WeakReference<NamespaceChangeStreamShortPoller> pollerRef,
      final NetworkMonitor networkMonitor,
      final Logger logger
  ) {
    this.pollerRef = pollerRef;
    this.networkMonitor = networkMonitor;
    this.logger = logger;
  }

  @Override
  public synchronized void run() {
    do {
      final NamespaceChangeStreamShortPoller poller = pollerRef.get();
      if (poller == null) {
        return;
      }

      boolean successful = false;
      try {
        successful = poller.poll();
      } catch (final Throwable t) {
        logger.error("error happened during polling:", t);
      }

      try {
        if (successful) {
          wait(SHORT_SLEEP_MILLIS);
        } else {
          wait(LONG_SLEEP_MILLIS);
        }
      } catch (final InterruptedException e) {
        return;
      }
    } while (true);
  }

  @Override
  public synchronized void onNetworkStateChanged() {
    if (networkMonitor.isConnected()) {
      notify();
    }
  }
}
