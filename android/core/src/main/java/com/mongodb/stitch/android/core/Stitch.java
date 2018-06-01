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

package com.mongodb.stitch.android.core;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.util.Log;
import com.mongodb.stitch.android.core.internal.StitchAppClientImpl;
import com.mongodb.stitch.android.core.internal.common.SharedPreferencesStorage;
import com.mongodb.stitch.android.core.internal.net.AndroidNetworkMonitor;
import com.mongodb.stitch.core.StitchAppClientConfiguration;
import com.mongodb.stitch.core.internal.net.OkHttpTransport;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * The entry point into the Android SDK. This class is responsible for allowing one to initialize
 * {@link StitchAppClient}s and work with Stitch.
 */
public final class Stitch {
  private static final String DEFAULT_BASE_URL = "https://stitch.mongodb.com";
  private static final Long DEFAULT_DEFAULT_REQUEST_TIMEOUT = 15000L;
  private static final String TAG = Stitch.class.getSimpleName();
  private static final Map<String, StitchAppClientImpl> appClients = new HashMap<>();
  private static boolean initialized;
  private static String localAppVersion;
  private static String defaultClientAppId;
  private static String localAppName;
  private static Context applicationContext;

  /**
   * Initializes the Stitch SDK so that app clients can be created.
   *
   * @param context An Android context value.
   */
  public static synchronized void initialize(final Context context) {
    if (initialized) {
      return;
    }
    applicationContext = context.getApplicationContext();

    final String packageName = applicationContext.getPackageName();
    localAppName = packageName;

    final PackageManager manager = applicationContext.getPackageManager();
    try {
      final PackageInfo pkgInfo = manager.getPackageInfo(packageName, 0);
      localAppVersion = pkgInfo.versionName;
    } catch (final NameNotFoundException e) {
      Log.d(TAG, "Failed to get version of application, will not send in device info.");
    }

    initialized = true;
    Log.d(TAG, "Initialized android SDK");
  }

  private static synchronized void ensureInitialized() {
    if (!initialized) {
      throw new IllegalStateException("Stitch not initialized yet; please call initialize() first");
    }
  }

  /**
   * Gets the default initialized app client. If one has not been set, then an error will be thrown.
   * {@link com.mongodb.stitch.android.core.internal.StitchInitProvider} will normally automatically
   * initialize the default app client using configured resource values but {@link
   * Stitch#initializeDefaultAppClient(String)} can also be used to
   * initialize the default app client.
   *
   * @return The default initialized app client.
   */
  public static synchronized StitchAppClient getDefaultAppClient() {
    ensureInitialized();
    if (defaultClientAppId == null) {
      throw new IllegalStateException("default app client has not yet been initialized/set");
    }
    return appClients.get(defaultClientAppId);
  }

  /**
   * Gets an app client by its client app id if it has been initialized; throws if none can be
   * found.
   *
   * @param clientAppId the client app id of the app client to get.
   * @return the app client associated with the client app id.
   */
  public static synchronized StitchAppClient getAppClient(
      @Nonnull final String clientAppId
  ) {
    ensureInitialized();
    if (!appClients.containsKey(clientAppId)) {
      throw new IllegalStateException(
          String.format("client for app '%s' has not yet been initialized", clientAppId));
    }
    return appClients.get(clientAppId);
  }

  /**
   * Checks if an app client has been initialized by its client app id.
   *
   * @param clientAppId the client app id to search for.
   * @return if an app client has been initialized by its client app id.
   */
  public static synchronized boolean hasAppClient(final String clientAppId) {
    ensureInitialized();
    return appClients.containsKey(clientAppId);
  }

  /**
   * Initializes the default app client for Stitch to use when using {@link
   * Stitch#getDefaultAppClient()}. Can only be called once.
   *
   * @param clientAppId the client app id to initialize an app client for.
   * @return the app client that was just initialized.
   */
  public static synchronized StitchAppClient initializeDefaultAppClient(
      @Nonnull final String clientAppId
  ) {
    return initializeDefaultAppClient(
        clientAppId,
        new StitchAppClientConfiguration.Builder().build());
  }

  /**
   * Initializes the default app client for Stitch to use when using {@link
   * Stitch#getDefaultAppClient()}. Can only be called once.
   *
   * @param clientAppId the client app id to initialize an app client for.
   * @param config the configuration to use to build the app client.
   * @return the app client that was just initialized.
   */
  public static synchronized StitchAppClient initializeDefaultAppClient(
      @Nonnull final String clientAppId,
      @Nonnull final StitchAppClientConfiguration config
  ) {
    ensureInitialized();
    if (clientAppId == null || clientAppId.isEmpty()) {
      throw new IllegalArgumentException("clientAppId must be set to a non-empty string");
    }
    if (defaultClientAppId != null) {
      throw new IllegalStateException(
          String.format(
              "default app can only be set once; currently set to '%s'", defaultClientAppId));
    }
    final StitchAppClient client = initializeAppClient(clientAppId, config);
    defaultClientAppId = clientAppId;
    return client;
  }

  /**
   * Initializes an app client for Stitch to use when using {@link Stitch#getAppClient(String)}}.
   * Can only be called once per client app id.
   *
   * @param clientAppId the client app id to initialize an app client for.
   * @return the app client that was just initialized.
   */
  public static synchronized StitchAppClient initializeAppClient(
      @Nonnull final String clientAppId
  ) {
    return initializeAppClient(clientAppId, new StitchAppClientConfiguration.Builder().build());
  }

  /**
   * Initializes an app client for Stitch to use when using {@link Stitch#getAppClient(String)}}.
   * Can only be called once per client app id.
   *
   * @param clientAppId the client app id to initialize an app client for.
   * @param config the configuration to use to build the app client.
   * @return the app client that was just initialized.
   */
  public static synchronized StitchAppClient initializeAppClient(
      @Nonnull final String clientAppId,
      @Nonnull final StitchAppClientConfiguration config
  ) {
    ensureInitialized();
    if (clientAppId == null || clientAppId.isEmpty()) {
      throw new IllegalArgumentException("clientAppId must be set to a non-empty string");
    }

    if (appClients.containsKey(clientAppId)) {
      throw new IllegalStateException(
          String.format("client for app '%s' has already been initialized", clientAppId));
    }

    final StitchAppClientConfiguration.Builder builder = config.builder();
    final String sharedPrefFileName = SharedPreferencesStorage.getFileName(clientAppId);
    if (builder.getStorage() == null) {
      builder.withStorage(
          new SharedPreferencesStorage(
              applicationContext.getSharedPreferences(sharedPrefFileName, Context.MODE_PRIVATE)));
    }
    if (builder.getDataDirectory() == null || builder.getDataDirectory().isEmpty()) {
      builder.withDataDirectory(applicationContext.getApplicationInfo().dataDir);
    }
    if (builder.getTransport() == null) {
      builder.withTransport(new OkHttpTransport());
    }
    if (builder.getDefaultRequestTimeout() == null) {
      builder.withDefaultRequestTimeout(DEFAULT_DEFAULT_REQUEST_TIMEOUT);
    }
    if (builder.getBaseUrl() == null || builder.getBaseUrl().isEmpty()) {
      builder.withBaseUrl(DEFAULT_BASE_URL);
    }
    if (builder.getLocalAppName() == null || builder.getLocalAppName().isEmpty()) {
      builder.withLocalAppName(localAppName);
    }
    if (builder.getLocalAppVersion() == null
        || builder.getLocalAppVersion().isEmpty()) {
      builder.withLocalAppVersion(localAppVersion);
    }
    if (builder.getNetworkMonitor() == null) {
      final ConnectivityManager connectivityManager
          = (ConnectivityManager) applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
      builder.withNetworkMonitor(new AndroidNetworkMonitor(connectivityManager));
    }

    final StitchAppClientImpl client = new StitchAppClientImpl(clientAppId, builder.build());
    appClients.put(clientAppId, client);
    return client;
  }
}
