package com.mongodb.stitch.android.core.internal;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import com.mongodb.stitch.android.core.Stitch;
import com.mongodb.stitch.core.StitchAppClientConfiguration;

/**
 * StitchInitProvider's sole purpose is to automatically initialize the SDK with the application
 * this provider is bound to. The manifest will ensure that there is a 1:1 relationship with this
 * provider and the application. This is a total abuse of ContentProvider but it allows for less
 * configuration on the user's part.
 */
public final class StitchInitProvider extends ContentProvider {
  private static final String TAG = StitchInitProvider.class.getSimpleName();

  @Override
  public boolean onCreate() {
    try {
      Stitch.initialize(getContext());
    } catch (final Exception e) {
      Log.e(TAG, "Error automatically initializing the MongoDB Stitch SDK", e);
    }

    try {
      tryInitializeDefaultApp();
    } catch (final Exception ex) {
      Log.d(TAG, "Failed to initialize default stitch app from settings file", ex);
    }
    return false;
  }

  private void tryInitializeDefaultApp() {
    if (getContext() == null || getContext().getResources() == null) {
      return;
    }

    final int clientAppIdId =
        getContext()
            .getResources()
            .getIdentifier(
                AppSettingsResourceNames.CLIENT_APP_ID, "string", getContext().getPackageName());
    if (clientAppIdId == 0) {
      return;
    }

    final String clientAppId = getContext().getResources().getString(clientAppIdId);

    final int baseURLId =
        getContext()
            .getResources()
            .getIdentifier(
                AppSettingsResourceNames.BASE_URL, "string", getContext().getPackageName());

    if (baseURLId == 0) {
      Stitch.initializeDefaultAppClient(StitchAppClientConfiguration.Builder.forApp(clientAppId));
    } else {
      final String baseURL = getContext().getResources().getString(baseURLId);
      Stitch.initializeDefaultAppClient(
          StitchAppClientConfiguration.Builder.forApp(clientAppId, baseURL));
    }
    Log.i(TAG, String.format("Automatically initialized app '%s' as default app", clientAppId));
  }

  @NonNull
  @Override
  public Cursor query(
      @NonNull Uri uri,
      @Nullable String[] strings,
      @Nullable String s,
      @Nullable String[] strings1,
      @Nullable String s1) {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public String getType(@NonNull Uri uri) {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public Uri insert(@NonNull Uri uri, @Nullable ContentValues contentValues) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int delete(@NonNull Uri uri, @Nullable String s, @Nullable String[] strings) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int update(
      @NonNull Uri uri,
      @Nullable ContentValues contentValues,
      @Nullable String s,
      @Nullable String[] strings) {
    throw new UnsupportedOperationException();
  }

  private static class AppSettingsResourceNames {
    private static final String CLIENT_APP_ID = "stitch_client_app_id";
    private static final String BASE_URL = "stitch_base_url";
  }
}
