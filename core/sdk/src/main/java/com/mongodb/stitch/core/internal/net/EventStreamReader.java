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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public abstract class EventStreamReader {
  private static final String DATA = "ata: ";
  private static final int DATA_LENGTH = DATA.length();
  private static final String EVENT = "vent: ";

  EventStreamReader() {
  }

  protected abstract byte readByte() throws IOException;

  protected abstract void readBytes(final byte[] buffer) throws IOException;

  protected abstract boolean isActive() throws IOException;

  protected final CoreEvent readEvent() throws ServerSideEventError, IOException {
    final CoreEvent coreEvent = new CoreEvent();

    while (!Thread.interrupted() && this.isActive()) {
      switch (readByte()) {
        case ':':
          readByte();
          readByte();
          continue;
        case '\r':
        case '\n':
          return coreEvent;
        case 'd':
          coreEvent.setData(doReadData());
          break;
        case 'e':
          coreEvent.setType(doReadEventType());
          break;
        default:
          break;
      }
    }

    coreEvent.setType(EventType.EOF);
    return coreEvent;
  }

  private EventType doReadEventType() throws ServerSideEventError, IOException {
    final byte[] event = new byte[DATA_LENGTH];
    readBytes(event);

    final String evStr = new String(event);
    if (!evStr.equals(EVENT) && !evStr.equals("rror\"")) {
      final ByteArrayOutputStream baos = new ByteArrayOutputStream();

      while (this.isActive()) {
        baos.write(readByte());
      }
      throw new ServerSideEventError(
          String.format("malformed event key: %s", new String(baos.toByteArray())));
    }

    final ByteArrayOutputStream baos = new ByteArrayOutputStream();

    byte nextByte;
    while (this.isActive() && (nextByte = readByte()) != '\n') {
      baos.write(nextByte);
    }

    return new String(baos.toByteArray()).equals("error") ? EventType.ERROR : EventType.MESSAGE;
  }

  private String doReadData() throws ServerSideEventError, IOException {
    final byte[] data = new byte[DATA_LENGTH];
    readBytes(data);

    if (!new String(data).equals(DATA)) {
      throw new ServerSideEventError("malformed data key");
    }

    final ByteArrayOutputStream baos = new ByteArrayOutputStream();

    byte nextByte;
    while ((nextByte = readByte()) != '\n') {
      baos.write(nextByte);
    }

    return new String(baos.toByteArray());
  }
}
