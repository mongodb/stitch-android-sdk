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

import com.mongodb.MongoInterruptedException;
import com.mongodb.stitch.core.internal.net.NetworkMonitor;

import java.lang.ref.WeakReference;

import org.bson.diagnostics.Logger;

/**
 * This runner runs {@link DataSynchronizer#doSyncPass()} on a periodic interval.
 */
class NamespaceChangeStreamRunner implements Runnable {
  private static final Long RETRY_SLEEP_MILLIS = 5000L;

  private final WeakReference<NamespaceChangeStreamListener> listenerRef;
  private final NetworkMonitor networkMonitor;
  private final Logger logger;

  NamespaceChangeStreamRunner(
      final WeakReference<NamespaceChangeStreamListener> listenerRef,
      final NetworkMonitor networkMonitor,
      final Logger logger
  ) {
    this.listenerRef = listenerRef;
    this.networkMonitor = networkMonitor;
    this.logger = logger;
  }

  @Override
  public synchronized void run() {
    final NamespaceChangeStreamListener listener = listenerRef.get();
    if (listener == null) {
      return;
    }

    do {
      boolean isOpen = listener.isOpen();
      if (!isOpen) {
        try {
          isOpen = listener.openStream();
        } catch (final MongoInterruptedException ex) {
          logger.error("NamespaceChangeStreamRunner::run error happened while opening stream:", ex);
          System.out.println("REALLY BAD THING MONGO BOY");
          return;
        } catch (final Throwable t) {
          logger.error("NamespaceChangeStreamRunner::run error happened while opening stream:", t);
          if (Thread.currentThread().isInterrupted()) {
            System.out.println("REALLY BAD THING HERE");
            return;
          }
        }

        try {
          if (!isOpen) {
            wait(RETRY_SLEEP_MILLIS);
          }
        } catch (final InterruptedException e) {
          return;
        }
      }

      if (isOpen) {
        listener.storeNextEvent();
      }
    } while (networkMonitor.isConnected() && !Thread.currentThread().isInterrupted());
  }
}
