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

package com.mongodb.stitch.core.auth.internal;

import java.lang.ref.WeakReference;

final class AccessTokenRefresher<T extends CoreStitchUser> implements Runnable {
  private static final Long SLEEP_MILLIS = 60000L; // how long to sleep for between refreshes.
  private static final Long EXPIRATION_WINDOW_SECS = 300L;

  private final WeakReference<CoreStitchAuth<T>> authRef;

  AccessTokenRefresher(final WeakReference<CoreStitchAuth<T>> authRef) {
    this.authRef = authRef;
  }

  @Override
  public void run() {
    do {
      if (!checkRefresh()) {
        return;
      }

      try {
        Thread.sleep(SLEEP_MILLIS);
      } catch (final InterruptedException e) {
        return;
      }
    } while (true);
  }

  public boolean checkRefresh() {
    final CoreStitchAuth<T> auth = authRef.get();
    if (auth == null) {
      return false;
    }

    if (!auth.isLoggedIn()) {
      return true;
    }

    final AuthInfo info = auth.getAuthInfo();
    if (info == null) {
      return true;
    }

    final Jwt jwt;
    try {
      jwt = Jwt.fromEncoded(info.getAccessToken());
    } catch (final Exception e) {
      return true;
    }

    // Check if it's time to refresh the access token
    if (System.currentTimeMillis() / 1000L < jwt.getExpires() - EXPIRATION_WINDOW_SECS) {
      return true;
    }

    try {
      auth.refreshAccessToken();
    } catch (final Exception e) {
      // Swallow
    }

    return true;
  }
}
