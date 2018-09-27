package com.mongodb.stitch.core.internal.net;

import java.io.IOException;

import okio.BufferedSource;
import okio.ByteString;

public class OkHttpEventStream extends EventStreamReader implements EventStream {
  private final BufferedSource source;

  OkHttpEventStream(BufferedSource source) {
    this.source = source;
  }

  @Override
  public CoreEvent nextEvent() throws SSEError, IOException {
    return readEvent();
  }

  @Override
  protected byte readByte() throws IOException {
    return this.source.readByte();
  }

  @Override
  protected boolean exhausted() throws IOException {
    return this.source.exhausted();
  }

  @Override
  protected void readBytes(byte[] buffer) throws IOException {
    this.source.read(buffer);
  }

  @Override
  protected long indexOf(String element) throws IOException {
    return this.source.indexOf(ByteString.encodeUtf8(element));
  }

  @Override
  public boolean isOpen() {
    return this.source.isOpen();
  }

  @Override
  public void close() throws IOException {
    this.source.close();
  }
}
