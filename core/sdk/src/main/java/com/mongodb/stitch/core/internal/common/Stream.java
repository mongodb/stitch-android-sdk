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

package com.mongodb.stitch.core.internal.common;

import com.mongodb.stitch.core.internal.net.Event;
import com.mongodb.stitch.core.internal.net.EventStream;

import java.io.IOException;

import org.bson.codecs.Decoder;
import org.bson.codecs.configuration.CodecRegistry;


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

  public T next() throws Exception {
    return this.nextEvent().getData();
  }

  public Event<T> nextEvent() throws Exception {
    return Event.fromCoreEvent(this.eventStream.nextEvent(), this.decoder);
  }

  public boolean isOpen() {
    return this.eventStream.isOpen();
  }

  public void close() throws IOException {
    this.eventStream.close();
  }
}
