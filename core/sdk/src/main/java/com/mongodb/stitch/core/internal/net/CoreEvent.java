package com.mongodb.stitch.core.internal.net;

public class CoreEvent {
  private EventType type;
  private String data;

  CoreEvent() {
    this.type = EventType.MESSAGE;
  }

  CoreEvent(EventType type, String data) {
    this.type = type;
    this.data = data;
  }

  void setType(EventType type) {
    this.type = type;
  }

  void setData(String data) {
    this.data = data;
  }

  public EventType getType() {
    return type;
  }

  public String getData() {
    return data;
  }
}
