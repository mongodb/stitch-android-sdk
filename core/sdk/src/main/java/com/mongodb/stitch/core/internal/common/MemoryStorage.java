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
