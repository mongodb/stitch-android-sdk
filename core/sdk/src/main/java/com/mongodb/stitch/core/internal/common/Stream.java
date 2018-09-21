package com.mongodb.stitch.core.internal.common;

import com.mongodb.stitch.core.internal.net.Event;
import com.mongodb.stitch.core.internal.net.EventStream;

import org.bson.codecs.configuration.CodecRegistry;

import java.io.IOException;

public class Stream<T> {
  private final EventStream eventStream;
  private final Class<T> resultClass;
  private final CodecRegistry codecRegistry;

  public Stream(EventStream eventStream,
                Class<T> resultClass,
                CodecRegistry codecRegistry) {
    this.eventStream = eventStream;
    this.resultClass = resultClass;
    this.codecRegistry = codecRegistry;
  }

  public T stream() throws Exception {
    return this.streamEvent().getData();
  }

  public Event<T> streamEvent() throws Exception {
    return Event.fromCoreEvent(this.eventStream.nextEvent(), this.resultClass, this.codecRegistry);
  }

  public boolean isOpen() {
    return this.eventStream.isOpen();
  }

  public void close() throws IOException {
    this.eventStream.close();
  }
}
