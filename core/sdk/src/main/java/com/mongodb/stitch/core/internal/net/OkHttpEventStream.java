package com.mongodb.stitch.core.internal.net;

import java.io.IOException;

import okio.BufferedSource;

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
  protected byte[] readBytes(int start, int end) throws IOException {
    byte[] bytes = new byte[end - start];
    this.source.read(bytes, start, end - start);
    return bytes;
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
