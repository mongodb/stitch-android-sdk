/*
 * Copyright 2018-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.stitch.android.services.mongodb.remote;

import com.google.android.gms.tasks.Task;

import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.core.services.mongodb.remote.BaseChangeEvent;
import com.mongodb.stitch.core.services.mongodb.remote.ChangeStream;
import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener;
import com.mongodb.stitch.core.services.mongodb.remote.sync.BaseChangeEventListener;

import java.io.IOException;
import java.util.concurrent.Callable;

/**
 * An implementation of {@link com.mongodb.stitch.core.services.mongodb.remote.ChangeStream} that
 * returns each event as a {@link Task}.
 *
 * @param <DocumentT> The type of the full document on the underlying change event to be returned
 *                    asynchronously.
 * @param <ChangeEventT> The type of MongoDB change event that this stream internally returns.
 */
public class AsyncChangeStream<DocumentT, ChangeEventT extends BaseChangeEvent<DocumentT>> {
  private final TaskDispatcher dispatcher;
  private final ChangeStream<ChangeEventT> stream;

  /**
   * Initializes a passthrough change stream with the provided underlying event stream.
   *
   * @param stream The event stream.
   * @param dispatcher The event dispatcher.
   */
  public AsyncChangeStream(final ChangeStream<ChangeEventT> stream,
                           final TaskDispatcher dispatcher) {
    this.stream = stream;
    this.dispatcher = dispatcher;
  }

  /**
   * Adds a ChangeEventListener to the ChangeStream that will run on every event on the stream.
   * Multiple ChangeEventListeners can be added to any given stream and they will be removed
   * when the stream is closed or when the listener is removed.
   *
   * @param listener the ChangeEventListener
   */
  public void addChangeEventListener(
      final BaseChangeEventListener<DocumentT, ChangeEventT> listener) {
    stream.addChangeEventListener(listener);
  }

  /**
   * Remove a ChangeEventListener from the ChangeStream.
   *
   * @param listener the ChangeEventListener
   */
  public void removeChangeEventListener(
      final BaseChangeEventListener<DocumentT, ChangeEventT> listener) {
    stream.removeChangeEventListener(listener);
  }

  /**
   * Closes the underlying stream and removes all ChangeEventListeners.
   * @throws IOException If the underlying stream throws an {@link IOException} when it is closed.
   */
  public void close() throws IOException {
    stream.close();
  }

  /**
   * Optionally adds a listener that is notified when an attempt to retrieve the next event.
   * fails.
   *
   * @param exceptionListener The exception listener.
   */
  public void setExceptionListener(final ExceptionListener exceptionListener) {
    stream.setExceptionListener(exceptionListener);
  }

  /**
   * Indicates whether or not the change stream is currently open.
   * @return True if the underlying change stream is open.
   */
  public boolean isOpen() {
    return stream.isOpen();
  }

  /**
   * Indicates whether or not any ChangeStreamListeners are currently running.
   * @return True if the ChangeStreamListeners are running
   */
  public boolean listenersRunning() {
    return stream.listenersRunning();
  }

  /**
   * Returns a {@link Task} whose resolution gives the next event from the underlying stream.
   * @return task providing the next event
   * @throws IOException if the underlying stream throws an {@link IOException}
   */
  @SuppressWarnings("unchecked")
  public Task<ChangeEventT> nextEvent() throws IOException {
    if (stream.listenersRunning()) {
      throw new IllegalStateException("Cannot use nextEvent() while listeners are running");
    }

    return dispatcher.dispatchTask(new Callable<ChangeEventT>() {
      @Override
      public ChangeEventT call() throws Exception {
        return stream.nextEvent();
      }
    });
  }
}
