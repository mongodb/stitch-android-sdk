/*
 * Copyright 2018-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.stitch.core.internal.net;

import com.mongodb.stitch.core.StitchServiceErrorCode;
import com.mongodb.stitch.core.StitchServiceException;
import com.mongodb.stitch.core.internal.common.BsonUtils;

import org.bson.Document;
import org.bson.codecs.Decoder;

public final class Event<T> {
  private final T data;
  private final StitchServiceException error;
  private final EventType eventType;

  private Event(final EventType eventType, final String data, final Decoder<T> decoder) {
    this.eventType = eventType;

    switch (eventType) {
      case MESSAGE:
        this.data = BsonUtils.parseValue(data, decoder);
        this.error = null;
        break;
      case ERROR:
        final Document error = BsonUtils.parseValue(data, Document.class);
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

  public static <T> Event<T> fromCoreEvent(final CoreEvent coreEvent,
                                           final Decoder<T> decoder) {
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
