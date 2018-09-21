package com.mongodb.stitch.core.internal.net;

import com.mongodb.stitch.core.internal.common.BsonUtils;

import org.bson.codecs.configuration.CodecRegistry;

public class Event<T> {
  private final T data;
  private final EventType eventType;

  private Event(EventType eventType, String data, Class<T> resultClass, CodecRegistry codecRegistry) {
    this.eventType = eventType;

    if (eventType == EventType.MESSAGE) {
      this.data = BsonUtils.parseValue(data, resultClass, codecRegistry);
    } else {
      this.data = null;
    }
  }

  public static <T> Event<T> fromCoreEvent(CoreEvent coreEvent,
                                           Class<T> resultClass,
                                           CodecRegistry codecRegistry) {
    return new Event<>(coreEvent.getType(), coreEvent.getData(), resultClass, codecRegistry);
  }

  public T getData() {
    return data;
  }

  public EventType getEventType() {
    return eventType;
  }
}
