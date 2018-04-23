package com.mongodb.stitch.core.internal.common;

import java.util.HashMap;
import java.util.Map;

public final class MemoryStorage implements Storage {

  private final Map<String, String> store = new HashMap<>();

  public MemoryStorage() {}

  @Override
  public synchronized String get(final String key) {
    if (!store.containsKey(key)) {
      return null;
    }
    return this.store.get(key);
  }

  @Override
  public synchronized void set(final String key, final String value) {
    store.put(key, value);
  }

  @Override
  public synchronized void remove(final String key) {
    store.remove(key);
  }
}
