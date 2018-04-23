package com.mongodb.stitch.core.internal.common;

@SuppressWarnings("SameParameterValue")
public interface Storage {

  String get(final String key);

  void set(final String key, final String value);

  void remove(final String key);
}
