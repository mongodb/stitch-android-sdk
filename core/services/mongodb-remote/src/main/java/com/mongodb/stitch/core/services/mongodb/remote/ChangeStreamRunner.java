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

package com.mongodb.stitch.core.services.mongodb.remote;

import com.mongodb.stitch.core.services.mongodb.remote.sync.BaseChangeEventListener;
import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Enumeration;

/**
 * This runner gets the next event from a stream and executes all listeners.
 */
class ChangeStreamRunner implements Runnable, Closeable {
  private final WeakReference<ChangeStream> streamRef;

  ChangeStreamRunner(
      final WeakReference<ChangeStream> streamRef
  ) {
    this.streamRef = streamRef;
  }

  @Override
  public synchronized void run() {
    final ChangeStream stream = streamRef.get();
    if (stream == null) {
      return;
    }

    do {
      try {
        final BaseChangeEvent nextEvent = stream.nextEvent();
        if (nextEvent == null) {
          continue;
        }

        final Enumeration<BaseChangeEventListener> listeners = stream.getChangeEventListeners();
        boolean hasListeners = false;
        while (listeners.hasMoreElements()) {
          listeners.nextElement().onEvent(nextEvent.getDocumentKey(), nextEvent);
          hasListeners = true;
        }

        if (!hasListeners) {
          Thread.currentThread().interrupt();
        }
      } catch (Exception e) {
        // TODO: What to do here
        Thread.currentThread().interrupt();
      }
    } while (!Thread.currentThread().isInterrupted());
  }

  @Override
  public void close() throws IOException {
    final ChangeStream stream = streamRef.get();
    if (stream == null) {
      return;
    }

    // not sure actually if we should do this
    stream.removeAllChangeEventListeners();

    stream.close();
  }
}
