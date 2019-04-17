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

import java.io.Closeable;
import java.io.IOException;

import org.bson.BsonValue;

/**
 * User-level abstraction for a stream returning {@link ChangeEvent}s from the server.
 *
 * @param <EventT> The type returned to users of this API when the next event is requested. May be
 *                 the same as ChangeEventT, or may be an async wrapper around ChangeEventT.
 * @param <ChangeEventT> The underlying change event type returned to users by this API.
 */
public abstract class ChangeStream<EventT, ChangeEventT extends BaseChangeEvent>
    implements Closeable {
  private final Stream<ChangeEventT> stream;

  private ExceptionListener exceptionListener = null;

  protected ChangeStream(final Stream<ChangeEventT> stream) {
    if (stream == null) {
      throw new IllegalArgumentException("null stream passed to change stream");
    }
    this.stream = stream;
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
  public abstract EventT nextEvent() throws IOException;

  /**
   * Indicates whether or not the change stream is currently open.
   * @return True if the underlying change stream is open.
   */
  public boolean isOpen() {
    return stream.isOpen();
  }

  /**
   * Closes the underlying stream.
   * @throws IOException If the underlying stream throws an {@link IOException} when it is closed.
   */
  @Override
  public void close() throws IOException {
    stream.close();
  }

  protected Stream<ChangeEventT> getStream() {
    return this.stream;
  }

  protected ExceptionListener getExceptionListener() {
    return this.exceptionListener;
  }

  protected void dispatchError(final StitchEvent<ChangeEventT> event) {
    if (exceptionListener != null) {
      BsonValue documentId = null;
      if (event.getData() != null) {
        documentId = event.getData().getDocumentKey();
      }
      exceptionListener.onError(documentId, event.getError());
    }
  }
}
