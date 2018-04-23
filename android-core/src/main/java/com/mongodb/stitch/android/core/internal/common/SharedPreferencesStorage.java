package com.mongodb.stitch.android.core.internal.common;

import android.content.SharedPreferences;
import com.mongodb.stitch.core.internal.common.Storage;

public final class SharedPreferencesStorage implements Storage {

  private static final String SHARED_PREFERENCES_FILE_FORMAT =
      "com.mongodb.stitch.sdk.SharedPreferences.%s";

  private final SharedPreferences sharedPreferences;

  public SharedPreferencesStorage(final SharedPreferences sharedPreferences) {
    this.sharedPreferences = sharedPreferences;
  }

  public static String getFileName(final String namespace) {
    return String.format(SHARED_PREFERENCES_FILE_FORMAT, namespace);
  }

  @Override
  public String get(final String key) {
    if (!sharedPreferences.contains(key)) {
      return null;
    }
    return this.sharedPreferences.getString(key, "");
  }

  @Override
  public void set(final String key, final String value) {
    sharedPreferences.edit().putString(key, value).apply();
  }

  @Override
  public void remove(final String key) {
    sharedPreferences.edit().remove(key).apply();
  }
}
