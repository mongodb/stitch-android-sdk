package com.mongodb.stitch.core.internal.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public abstract class EventStreamReader {
  private static final String CRLF = "\r\n";
  private static final String DATA = "ata: ";
  private static final int DATA_LENGTH = DATA.length();
  private static final String EVENT = "vent: ";
  private static final int EVENT_LENGTH = EVENT.length();

  public EventStreamReader() {
  }

  protected abstract byte readByte() throws IOException;

  protected abstract void readBytes(final byte[] buffer) throws IOException;
//  protected abstract byte[] readBytes(final int start, final int end) throws IOException;

  protected abstract long indexOf(final String element) throws IOException;

  protected abstract boolean exhausted() throws IOException;

  protected final CoreEvent readEvent() throws SSEError, IOException {
    CoreEvent coreEvent = new CoreEvent();

    while (!this.exhausted()) {
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
      }
    }

    coreEvent.setType(EventType.EOF);
    return coreEvent;
  }

  private EventType doReadEventType() throws SSEError, IOException {
    byte[] event = new byte[DATA_LENGTH];
    readBytes(event);

    String evStr = new String(event);
    if (!evStr.equals(EVENT) && !evStr.equals("rror\"")) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();

      byte nextByte;
      while (!this.exhausted() && (nextByte = readByte()) != '\n') {
        baos.write(nextByte);
      }
      throw new SSEError("malformed event key");
    }

    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    byte nextByte;
    while (!this.exhausted() && (nextByte = readByte()) != '\n') {
      baos.write(nextByte);
    }

    return new String(baos.toByteArray()).equals("error") ? EventType.ERROR : EventType.MESSAGE;
  }

  private String doReadData() throws SSEError, IOException {
    byte[] data = new byte[DATA_LENGTH];
    readBytes(data);

    if (!new String(data).equals(DATA)) {
      throw new SSEError("malformed data key");
    }

    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    byte nextByte;
    while ((nextByte = readByte()) != '\n') {
      baos.write(nextByte);
    }

    return new String(baos.toByteArray());
  }
}
