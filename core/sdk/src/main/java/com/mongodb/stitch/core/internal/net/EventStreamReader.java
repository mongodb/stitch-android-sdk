package com.mongodb.stitch.core.internal.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public abstract class EventStreamReader {
  private static final String CRLF = "\r\n";
  private static final String DATA = "ata: ";
  private static final int DATA_LENGTH = DATA.length();
  private static final String EVENT = "vent: ";
  private static final int EVENT_LENGTH = EVENT.length();

  private int position = 0;

  public EventStreamReader() {
  }

  protected abstract byte readByte() throws IOException;

  protected abstract byte[] readBytes(final int start, final int end) throws IOException;

  protected final CoreEvent readEvent() throws SSEError, IOException {
    CoreEvent coreEvent = new CoreEvent();

    while (true) {
      switch (readByte()) {
        case '\r':
        case '\n':
          return coreEvent;
        case 'd':
          coreEvent.setData(doReadData());
          break;
        case 'e':
          coreEvent.setType(doReadEventType());
          break;
      }

      position++;
    }
  }

  private EventType doReadEventType() throws SSEError, IOException {
    String event = new String(readBytes(position, position + EVENT_LENGTH));

    if (!event.equals(EVENT)) {
      throw new SSEError("malformed event key");
    }

    position += EVENT_LENGTH;

    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    byte nextByte;
    while ((nextByte = readByte()) != '\n') {
      baos.write(nextByte);
      position++;
    }

    return new String(baos.toByteArray()).equals("error") ? EventType.ERROR : EventType.MESSAGE;
  }

  private String doReadData() throws SSEError, IOException {
    String data = new String(readBytes(position, position + DATA_LENGTH));

    if (!data.equals(DATA)) {
      throw new SSEError("malformed data key");
    }

    position += DATA_LENGTH;

    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    byte nextByte;
    while ((nextByte = readByte()) != '\n') {
      baos.write(nextByte);
      position++;
    }

    return new String(baos.toByteArray());
  }
}
