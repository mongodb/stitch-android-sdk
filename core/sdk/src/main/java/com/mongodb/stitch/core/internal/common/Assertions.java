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

import java.util.Map;

public final class Assertions {
  /**
   * Throw IllegalArgumentException if the value is null.
   *
   * @param name  the parameter name.
   * @param value the value that should not be null.
   * @param <T>   the value type.
   * @throws IllegalArgumentException if value is null.
   */
  public static <T> void notNull(final String name, final T value) {
    if (value == null) {
      throw new IllegalArgumentException(name + " can not be null");
    }
  }

  /**
   * Throw IllegalStateException if key is not present in map.
   * @param key the key to expect.
   * @param map the map to search.
   * @throws IllegalArgumentException if key is not in map.
   */
  public static void keyPresent(final String key, final Map<String, ?> map) {
    if (!map.containsKey(key)) {
      throw new IllegalStateException(
          String.format("expected %s to be present", key));
    }
  }
}
