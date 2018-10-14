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

import java.io.EOFException;
import java.io.IOException;

/**
 * Interpreter for server-sent events.
 *
 * See https://www.w3.org/TR/2009/WD-eventsource-20090421/#event-stream-interpretation for
 * information on processing.
 */
public abstract class EventStreamReader {
  private StringBuilder dataBuffer = new StringBuilder();
  private String eventName = "";
  private boolean doneOnce;

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

  private void processField(final String field, final String value) {
    // If the field name is "event"
    switch (field) {
      case "event":
        eventName = value;
        break;
      // If the field name is "data"
      case "data":
        // If the data buffer is not the empty string, then append a single U+000A LINE FEED
        // character to the data buffer.
        if (dataBuffer.length() != 0) {
          dataBuffer.append('\n');
        }
        dataBuffer.append(value);
        break;
      // If the field name is "id"
      case "id":
        // NOT IMPLEMENTED
        break;
      // If the field name is "retry"
      case "retry":
        // NOT IMPLEMENTED
        break;
      // Otherwise
      default:
        // The field is ignored.
        break;
    }
  }

  /**
   * Process the next event in a given stream.
   * @return the fully processed event
   * @throws IOException if a stream is in the wrong state, IO errors can be thrown
   */
  protected final Event processEvent() throws IOException {
    while (true) {
      String line;
      try {
        line = readLine();
      } catch (final EOFException ex) {
        if (doneOnce) {
          throw ex;
        }
        doneOnce = true;
        line = "";
      }

      // If the line is empty (a blank line), Dispatch the event, as defined below.
      if (line.isEmpty()) {
        // If the data buffer is an empty string, set the data buffer and the event name buffer to
        // the empty string and abort these steps.
        if (dataBuffer.length() == 0) {
          eventName = "";
          continue;
        }

        // If the event name buffer is not the empty string but is also not a valid NCName,
        // set the data buffer and the event name buffer to the empty string and abort these steps.
        // NOT IMPLEMENTED

        final Event.Builder eventBuilder = new Event.Builder();
        eventBuilder.withEventName(eventName.isEmpty() ? Event.MESSAGE_EVENT : eventName);
        eventBuilder.withData(dataBuffer.toString());

        // Set the data buffer and the event name buffer to the empty string.
        dataBuffer = new StringBuilder();
        eventName = "";

        return eventBuilder.build();
        // If the line starts with a U+003A COLON character (':')
      } else if (line.startsWith(":")) {
        // ignore the line
        // If the line contains a U+003A COLON character (':') character
      } else if (line.contains(":")) {
        // Collect the characters on the line before the first U+003A COLON character (':'),
        // and let field be that string.
        final int colonIdx = line.indexOf(":");
        final String field = line.substring(0, colonIdx);

        // Collect the characters on the line after the first U+003A COLON character (':'),
        // and let value be that string.
        // If value starts with a single U+0020 SPACE character, remove it from value.
        String value = line.substring(colonIdx + 1);
        value = value.startsWith(" ") ? value.substring(1) : value;

        processField(field, value);
      // Otherwise, the string is not empty but does not contain a U+003A COLON character (':')
      // character
      } else {
        processField(line, "");
      }
    }
  }
}
