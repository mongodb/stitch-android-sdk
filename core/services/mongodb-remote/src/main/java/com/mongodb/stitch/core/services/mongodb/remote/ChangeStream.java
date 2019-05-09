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

package com.mongodb.stitch.core.services.mongodb.remote;

import com.mongodb.stitch.core.internal.net.StitchEvent;
import com.mongodb.stitch.core.internal.net.Stream;
import com.mongodb.stitch.core.services.mongodb.remote.sync.BaseChangeEventListener;

import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;

import org.bson.BsonValue;

/**
 * User-level abstraction for a stream returning {@link ChangeEvent}s from the server.
 *
 * @param <EventT> The type of underlying event for this ChangeStream.
 */
public class ChangeStream<EventT extends BaseChangeEvent> implements Closeable {

  private final Stream<EventT> internalStream;
  private ExceptionListener exceptionListener = null;
  private final ConcurrentHashMap<BaseChangeEventListener, Boolean> listeners;
  Thread runnerThread;

  public ChangeStream(final Stream<EventT> stream) {
    if (stream == null) {
      throw new IllegalArgumentException("null stream passed to change stream");
    }
    this.internalStream = stream;
    listeners = new ConcurrentHashMap<>();
  }

  /**
   * Optionally adds a listener that is notified when an attempt to retrieve the next event
   * fails.
   *
   * @param exceptionListener The exception listener.
   */
  public void setExceptionListener(final ExceptionListener exceptionListener) {
    this.exceptionListener = exceptionListener;
  }

  /**
   * Returns the next event available from the stream.
   *
   * @return The next event.
   * @throws IOException If the underlying stream throws an {@link IOException}
   */
  public EventT nextEvent() throws IOException {
    final StitchEvent<EventT> nextEvent = getInternalStream().nextEvent();

    if (nextEvent == null) {
      return null;
    }

    if (nextEvent.getError() != null) {
      dispatchError(nextEvent);
      return null;
    }

    return nextEvent.getData();
  }

  /**
   * Adds a ChangeEventListener to the ChangeStream that will run on every event on the stream.
   * Multiple ChangeEventListeners can be added to any given stream and they will be removed
   * when the stream is closed or when the listener is removed.
   *
   * @param listener the ChangeEventListener
   */
  public void addChangeEventListener(BaseChangeEventListener listener) {
    listeners.putIfAbsent(listener, true);
    if (!listenersRunning()) {
      runnerThread = new Thread(new ChangeStreamRunner(new WeakReference<>(this)));
      runnerThread.start();
    }
  }

  /**
   * Remove a ChangeEventListener from the ChangeStream.
   *
   * @param listener the ChangeEventListener
   */
  public void removeChangeEventListener(BaseChangeEventListener listener) {
    listeners.remove(listener);
  }

  /**
   * Indicates whether or not the change stream is currently open.
   * @return True if the underlying change stream is open.
   */
  public boolean isOpen() {
    return internalStream.isOpen();
  }

  /**
   * Indicates whether or not any ChangeStreamListeners are currently running
   * @return True if the ChangeStreamListeners are running
   */
  public boolean listenersRunning() {
    if (runnerThread == null) {
      return false;
    }
    return runnerThread.isAlive();
  }

  /**
   * Closes the underlying stream and removes all ChangeEventListeners.
   * @throws IOException If the underlying stream throws an {@link IOException} when it is closed.
   */
  @Override
  public void close() throws IOException {
    internalStream.close();
    runnerThread = null;
  }

  protected void removeAllChangeEventListeners() {
    listeners.clear();
  }

  protected Enumeration<BaseChangeEventListener> getChangeEventListeners() {
    return listeners.keys();
  }

  protected void startListeners() {
      if (runnerThread != null) {
        return;
      }
      runnerThread = new Thread(new ChangeStreamRunner(new WeakReference<>(this)));
      runnerThread.start();
  }

  protected Stream<EventT> getInternalStream() {
    return this.internalStream;
  }

  protected ExceptionListener getExceptionListener() {
    return this.exceptionListener;
  }

  protected void dispatchError(final StitchEvent<EventT> event) {
    if (exceptionListener != null) {
      BsonValue documentId = null;
      if (event.getData() != null) {
        documentId = event.getData().getDocumentKey();
      }
      exceptionListener.onError(documentId, event.getError());
    }
  }
}

