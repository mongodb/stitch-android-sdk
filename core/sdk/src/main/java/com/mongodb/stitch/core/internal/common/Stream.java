package com.mongodb.stitch.core.internal.common;

import com.mongodb.stitch.core.StitchServiceException;
import com.mongodb.stitch.core.internal.net.Event;
import com.mongodb.stitch.core.internal.net.EventStream;

import org.bson.codecs.Decoder;
import org.bson.codecs.configuration.CodecRegistry;

import java.io.IOException;
import java.util.concurrent.SynchronousQueue;

public class Stream<T> {
  private final EventStream eventStream;
  private final Decoder<T> decoder;

  public Stream(EventStream eventStream,
                Decoder<T> decoder) {
    this.eventStream = eventStream;
    this.decoder = decoder;
  }

  public Stream(EventStream eventStream,
                Class<T> resultClass,
                CodecRegistry codecRegistry) {
    this.eventStream = eventStream;
    this.decoder = codecRegistry.get(resultClass);
  }

  public T next() throws Exception {
    return this.nextEvent().getData();
  }

  public Event<T> nextEvent() throws Exception {
    return Event.fromCoreEvent(this.eventStream.nextEvent(), this.decoder);
  }

  public boolean isOpen() {
    return this.eventStream.isOpen();
  }

  public void close() throws IOException {
    this.eventStream.close();
  }
}
