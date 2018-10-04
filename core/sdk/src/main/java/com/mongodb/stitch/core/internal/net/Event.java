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

import javax.annotation.Nonnull;

/**
 * Any given server-sent event.
 *
 * See https://www.w3.org/TR/2009/WD-eventsource-20090421/.
 */
public class Event {
  /**
   * Default event name for unnamed event streams
   */
  static final String MESSAGE_EVENT = "message";

  /**
   * The name of this event
   */
  @Nonnull private final String eventName;

  /**
   * The data in the event stream. A single event
   * can contains multiple lines of data.
   */
  @Nonnull private final String data;

  private Event(@Nonnull String eventName, @Nonnull String data) {
    this.eventName = eventName;
    this.data = data;
  }

  /**
   * Builder for building a server-sent event. Events should be processed
   * line by line and built while processing.
   *
   * See https://www.w3.org/TR/2009/WD-eventsource-20090421/#event-stream-interpretation.
   */
  public static class Builder {
    private String eventName;
    private String data;

    public Builder withData(@Nonnull final String data) {
      this.data = data;
      return this;
    }

    public Builder withEventName(final String eventName) {
      this.eventName = eventName;
      return this;
    }

    public Event build() {
      if (this.eventName == null) {
        this.eventName = MESSAGE_EVENT;
      }

      return new Event(this.eventName, this.data);
    }
  }

  @Nonnull
  public String getEventName() {
    return eventName;
  }

  @Nonnull
  public String getData() {
    return data;
  }
}
