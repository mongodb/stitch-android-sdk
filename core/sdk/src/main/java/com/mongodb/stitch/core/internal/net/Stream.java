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

package com.mongodb.stitch.core.internal.net;

import java.io.IOException;

import org.bson.codecs.Decoder;
import org.bson.codecs.configuration.CodecRegistry;

/**
 * Stitch stream that sits on top of an {@link EventStream}.
 *
 * Clean interface to read events from a stream as some type T.
 * @param <T> type to decode events into
 */
public class Stream<T> {
  private final EventStream eventStream;
  private final Decoder<T> decoder;

  public Stream(final EventStream eventStream,
                final Decoder<T> decoder) {
    this.eventStream = eventStream;
    this.decoder = decoder;
  }

  public Stream(final EventStream eventStream,
                final Class<T> resultClass,
                final CodecRegistry codecRegistry) {
    this.eventStream = eventStream;
    this.decoder = codecRegistry.get(resultClass);
  }

  /**
   * Fetch the next event from a given stream
   * @return the next event
   * @throws Exception any exception that could occur
   */
  public StitchEvent<T> nextEvent() throws Exception {
    return StitchEvent.fromEvent(this.eventStream.nextEvent(), this.decoder);
  }

  /**
   * Whether or not the underlying event straem is still open.
   * @return true is open, false if not
   */
  public boolean isOpen() {
    return this.eventStream.isOpen();
  }

  /**
   * Close the underlying event stream.
   * @throws IOException i/o exceptions relative to closing
   */
  public void close() throws IOException {
    this.eventStream.close();
  }

  /**
   * Close the underlying connection to the event stream.
   */
  public void cancel() {
    this.eventStream.cancel();
  }
}
