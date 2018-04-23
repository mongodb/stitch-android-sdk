package com.mongodb.stitch.core.auth.internal;

import java.lang.ref.WeakReference;

final class AccessTokenRefresher<T extends CoreStitchUser> implements Runnable {
  private static final Long SLEEP_MILLIS = 60000L;
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

    final JWT jwt;
    try {
      jwt = JWT.fromEncoded(info.accessToken);
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
