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

package com.mongodb.stitch.core.testutils;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.Callable;

public final class Assert {

  private Assert() {

  }

  /**
   * Asserts that the given function throws the given exception class.
   */
  public static void assertThrows(
      final Callable<?> fun,
      final Class<? extends Exception> exClazz
  ) {
    try {
      fun.call();
      fail("expected to throw and catch a " + exClazz.getSimpleName());
    } catch (final Exception ex) {
      assertTrue(exClazz.isInstance(ex));
    }
  }

  /**
   * Asserts that the given function throws.
   */
  public static void assertThrows(
      final Callable<?> fun
  ) {
    try {
      fun.call();
      fail("expected to throw");
    } catch (final Throwable ignored) {
      // do nothing
    }
  }
}
