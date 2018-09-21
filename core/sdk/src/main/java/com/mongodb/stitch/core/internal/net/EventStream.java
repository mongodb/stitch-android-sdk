package com.mongodb.stitch.core.internal.net;

import java.io.IOException;

public interface EventStream {
  CoreEvent nextEvent() throws SSEError, IOException;
  boolean isOpen();
  void close() throws IOException;
}
