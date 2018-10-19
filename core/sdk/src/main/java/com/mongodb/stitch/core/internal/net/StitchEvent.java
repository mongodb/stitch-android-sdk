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

import javax.annotation.Nullable;

import org.bson.Document;
import org.bson.codecs.Decoder;


/**
 * Stitch abstraction of server-sent events.
 *
 * @param <T> type to decode data to
 */
public final class StitchEvent<T> {
  /**
   * Stitch event name for error messages
   */
  private static final String ERROR_EVENT_NAME = "error";

  /**
   * decoded data from the event
   */
  private final T data;

  /**
   * error from the event
   */
  private final StitchServiceException error;

  /**
   * name of this event
   */
  private final String eventName;

  private StitchEvent(final String eventName,
                      final String data,
                      final Decoder<T> decoder) {
    this.eventName = eventName;

    switch (this.eventName) {
      case ERROR_EVENT_NAME:
        String errorMsg;
        StitchServiceErrorCode errorCode;

        try {
          // parse the error as json
          // if it is not valid json, parse the body as seen in
          // StitchError#handleRequestError
          final Document errorDoc = BsonUtils.parseValue(data, Document.class);
          errorMsg = errorDoc.getString(ErrorFields.ERROR);
          errorCode = StitchServiceErrorCode.fromCodeName(
              errorDoc.getString(ErrorFields.ERROR_CODE));
        } catch (Exception e) {
          errorMsg = data;
          errorCode = StitchServiceErrorCode.UNKNOWN;
        }
        this.error = new StitchServiceException(errorMsg, errorCode);
        this.data = null;
        break;
      case Event.MESSAGE_EVENT:
        if (data != null) {
          this.data = BsonUtils.parseValue(data, decoder);
        } else {
          this.data = null;
        }
        this.error = null;
        break;
      default:
        this.data = null;
        this.error = null;
        break;
    }
  }

  /**
   * Convert a SSE to a Stitch SSE
   * @param event SSE to convert
   * @param decoder decoder for decoding data
   * @param <T> type to decode data to
   * @return a Stitch server-sent event
   */
  static <T> StitchEvent<T> fromEvent(final Event event,
                                      final Decoder<T> decoder) {
    return new StitchEvent<>(event.getEventName(), event.getData(), decoder);
  }

  @Nullable
  public T getData() {
    return data;
  }

  @Nullable
  public StitchServiceException getError() {
    return error;
  }

  public String getEventName() {
    return eventName;
  }

  private static class ErrorFields {
    private static final String ERROR = "error";
    private static final String ERROR_CODE = "error_code";
  }
}
