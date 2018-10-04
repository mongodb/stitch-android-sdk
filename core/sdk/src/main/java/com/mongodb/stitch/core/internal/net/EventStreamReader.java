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

/**
 * Interpreter for server-sent events.
 *
 * See https://www.w3.org/TR/2009/WD-eventsource-20090421/#event-stream-interpretation for
 * information on processing.
 */
public abstract class EventStreamReader {
  EventStreamReader() {
  }

  /**
   * Whether or not the stream is still active.
   *
   * @return true if active, false if not
   * @throws IOException if a stream is in the wrong state, IO errors can be thrown
   */
  protected abstract boolean isOpen() throws IOException;

  /**
   * Read the next line of a stream from a given source.
   * @return the next utf8 line
   * @throws IOException if a stream is in the wrong state, IO errors can be thrown
   */
  protected abstract String readLine() throws IOException;

  /**
   * Process the next event in a given stream.
   * @return the fully processed event
   * @throws IOException if a stream is in the wrong state, IO errors can be thrown
   */
  protected final Event processEvent() throws IOException {
    final Event.Builder eventBuilder = new Event.Builder();
    final StringBuilder dataBuffer = new StringBuilder();

    while (isOpen()) {
      final String line = readLine();

      if (line == null) {
        continue;
      }

      // if the line starts with a U+003A COLON character (':')
      if (line.startsWith(":")) {
        // ignore the line
        continue;
      }

      // if the line contains a U+003A COLON character (':') character
      if (line.contains(":")) {
        // collect the characters on the line before the first U+003A COLON character (':')...
        final String[] lineSplitOnColon = line.split(":", 2);
        // ...and let _field_ be that string
        final String field = lineSplitOnColon[0];

        final StringBuilder valueBuffer = new StringBuilder();
        // collect the characters on the line after the first U+003A COLON character (':')
        // and let value be that string. if value starts with a single U+0020 SPACE character,
        // remove it from value.
        for (int i = 1; i < lineSplitOnColon.length; i++) {
          valueBuffer.append(lineSplitOnColon[i].trim());
        }

        // if the field name is "data"
        if (field.equals("data")) {
          final String value = valueBuffer.toString();
          // if the data buffer is not the empty string,
          // then append a single U+000A LINE FEED character to the data buffer.
          // append the field value to the data buffer.
          if (!value.isEmpty()) {
            dataBuffer.append(value);
            dataBuffer.append("\n");
          }
        }

        // if the field name is "event"
        // set the event name buffer to field value.
        if (field.equals("event")) {
          eventBuilder.withEventName(valueBuffer.toString().trim());
        }
      }

      if ((line.isEmpty() && !dataBuffer.toString().isEmpty())
          || line.endsWith("\r") || line.endsWith("\n")) {
        eventBuilder.withData(dataBuffer.toString());
        break;
      }
    }

    return eventBuilder.build();
  }
}
