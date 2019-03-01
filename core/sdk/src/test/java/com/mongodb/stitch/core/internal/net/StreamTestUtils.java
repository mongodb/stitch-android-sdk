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

import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.bson.codecs.Decoder;
import org.mockito.Mockito;

/**
 * Utility methods for working with streams in tests by creating a real {@link Stream} object
 * with a mocked {@link EventStream} underneath.
 */
public final class StreamTestUtils {
  private StreamTestUtils() {
    // no-op
  }

  public static <T> Stream<T> createStream(final Decoder<T> decoder,
                                           final String... streamContent) throws IOException {
    return doCreateStream(decoder, true, streamContent);
  }

  public static <T> Stream<T> createClosedStream(final Decoder<T> decoder,
                                                 final String... streamContent) throws IOException {
    return doCreateStream(decoder, false, streamContent);
  }

  private static <T> Stream<T> doCreateStream(final Decoder<T> decoder,
                                            final boolean open,
                                            final String... streamContent)
      throws IOException {
    final EventStream eventStream = Mockito.mock(EventStream.class);

    final List<Event> streamEvents =
        Arrays.stream(streamContent)
            .map(c -> new Event.Builder().withData(c).build()).collect(Collectors.toList());

    final int numEvents = streamEvents.size();
    if (numEvents > 0) {
      final Object firstEvent = streamEvents.get(0);
      final Object[] subsequentEvents =
          streamEvents.subList(1, streamEvents.size()).toArray();

      doReturn(firstEvent, subsequentEvents).when(eventStream).nextEvent();
    }
    doReturn(open).when(eventStream).isOpen();

    return new Stream<>(eventStream, decoder);
  }
}
