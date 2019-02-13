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

import javax.annotation.Nullable;

public interface AuthMonitor {
  /**
   * Get whether or not the application client is currently
   * logged in.
   *
   * @return whether or not the application client is logged in
   * @throws InterruptedException will throw interruptibly
   */
  boolean isLoggedIn() throws InterruptedException;

  /**
   * Get whether or not the application client is currently
   * logged in.
   *
   * @return whether or not the application client is logged in,
   *         or false if the thread was interrupted
   */
  boolean tryIsLoggedIn();

  /**
   * Get the active user id from the applications
   * auth request client.
   *
   * @return active user id if there is one, null if not
   */
  @Nullable String getActiveUserId();
}
