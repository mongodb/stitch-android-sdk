package com.mongodb.stitch.core.internal.net;

import java.io.IOException;

import okio.Buffer;
import okio.BufferedSource;
import okio.ByteString;

public class OkHttpEventStream extends EventStreamReader implements EventStream {
  private final BufferedSource source;
  private final Buffer buffer;

  OkHttpEventStream(BufferedSource source) {
    this.source = source;
    this.buffer = source.buffer();
  }

  @Override
  public CoreEvent nextEvent() throws SSEError, IOException {
    return readEvent();
  }

  @Override
  protected byte readByte() throws IOException {
    return this.buffer.readByte();
  }

  @Override
  protected boolean exhausted() throws IOException {
    return this.buffer.exhausted();
  }

  @Override
  protected void readBytes(byte[] buffer) throws IOException {
    this.buffer.read(buffer);
  }

  @Override
  protected long indexOf(String element) throws IOException {
    return this.buffer.indexOf(ByteString.encodeUtf8(element));
  }

  @Override
  public boolean isOpen() {
    return this.source.isOpen();
  }

  @Override
  public void close() throws IOException {
//    this.source.buffer().clear();
//    this.source.buffer().close();
    this.buffer.close();
//    this.source.close();
  }
}
