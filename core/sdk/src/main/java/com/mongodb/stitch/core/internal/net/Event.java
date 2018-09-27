package com.mongodb.stitch.core.internal.net;

import com.mongodb.stitch.core.StitchServiceErrorCode;
import com.mongodb.stitch.core.StitchServiceException;
import com.mongodb.stitch.core.internal.common.BsonUtils;

import org.bson.Document;
import org.bson.codecs.Decoder;

public class Event<T> {
  private final T data;
  private final StitchServiceException error;
  private final EventType eventType;

  private Event(EventType eventType, String data, Decoder<T> decoder) {
    this.eventType = eventType;

    switch (eventType) {
      case MESSAGE:
        this.data = BsonUtils.parseValue(data, decoder);
        this.error = null;
        break;
      case ERROR:
        Document error = BsonUtils.parseValue(data, Document.class);
        this.error = new StitchServiceException(
            error.getString("error"),
            StitchServiceErrorCode.fromCodeName(error.getString("error_code")));
        this.data = null;
        break;
      default:
        this.data = null;
        this.error = null;
        break;
    }
  }

  public static <T> Event<T> fromCoreEvent(CoreEvent coreEvent,
                                           Decoder<T> decoder) {
    return new Event<>(coreEvent.getType(), coreEvent.getData(), decoder);
  }

  public T getData() {
    return data;
  }

  public StitchServiceException getError() {
    return error;
  }

  public EventType getEventType() {
    return eventType;
  }
}
