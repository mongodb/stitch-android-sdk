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
import android.util.Log;

import com.mongodb.stitch.android.core.internal.StitchAppClientImpl;
import com.mongodb.stitch.android.core.internal.common.SharedPreferencesStorage;
import com.mongodb.stitch.core.StitchAppClientConfiguration;
import com.mongodb.stitch.core.internal.net.OkHttpTransport;

import java.util.HashMap;
import java.util.Map;

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

  /**
   * Gets the default initialized app client. If one has not been set, then an error will be thrown.
   * {@link com.mongodb.stitch.android.core.internal.StitchInitProvider} will normally automatically
   * initialize the default app client using configured resource values but {@link
   * Stitch#initializeDefaultAppClient(StitchAppClientConfiguration.Builder)} can also be used to
   * initialize the default app client.
   *
   * @return The default initialized app client.
   */
  public static synchronized StitchAppClient getDefaultAppClient() {
    if (defaultClientAppId == null) {
      throw new IllegalStateException("default app client has not yet been initialized/set");
    }
    return appClients.get(defaultClientAppId);
  }

  /**
   * Gets an app client by its client app id if it has been initialized; throws if none can be
   * found.
   *
   * @param clientAppId The client app id of the app client to get.
   * @return The app client associated with the client app id.
   */
  public static synchronized StitchAppClient getAppClient(final String clientAppId) {
    if (!appClients.containsKey(clientAppId)) {
      throw new IllegalStateException(
          String.format("client for app '%s' has not yet been initialized", clientAppId));
    }
    return appClients.get(clientAppId);
  }

  /**
   * Initializes the default app client for Stitch to use when using {@link
   * Stitch#getDefaultAppClient()}. Can only be called once.
   *
   * @param configBuilder The configuration to use to build the app client.
   * @return The app client that was just initialized.
   */
  public static synchronized StitchAppClient initializeDefaultAppClient(
      final StitchAppClientConfiguration.Builder configBuilder) {
    final String clientAppId = configBuilder.getClientAppId();
    if (clientAppId == null || clientAppId.isEmpty()) {
      throw new IllegalArgumentException("clientAppId must be set to a non-empty string");
    }
    if (defaultClientAppId != null) {
      throw new IllegalStateException(
          String.format(
              "default app can only be set once; currently set to '%s'", defaultClientAppId));
    }
    final StitchAppClient client = initializeAppClient(configBuilder);
    defaultClientAppId = clientAppId;
    return client;
  }

  /**
   * Initializes an app client for Stitch to use when using {@link Stitch#getAppClient(String)}}.
   * Can only be called once per client app id.
   *
   * @param configBuilder The configuration to use to build the app client.
   * @return The app client that was just initialized.
   */
  public static synchronized StitchAppClient initializeAppClient(
      final StitchAppClientConfiguration.Builder configBuilder) {
    final String clientAppId = configBuilder.getClientAppId();
    if (clientAppId == null || clientAppId.isEmpty()) {
      throw new IllegalArgumentException("clientAppId must be set to a non-empty string");
    }

    if (appClients.containsKey(clientAppId)) {
      throw new IllegalStateException(
          String.format("client for app '%s' has already been initialized", clientAppId));
    }

    final String sharedPrefFileName = SharedPreferencesStorage.getFileName(clientAppId);
    if (configBuilder.getStorage() == null) {
      configBuilder.withStorage(
          new SharedPreferencesStorage(
              applicationContext.getSharedPreferences(sharedPrefFileName, Context.MODE_PRIVATE)));
    }
    if (configBuilder.getDataDirectory() == null || configBuilder.getDataDirectory().isEmpty()) {
      configBuilder.withDataDirectory(applicationContext.getApplicationInfo().dataDir);
    }
    if (configBuilder.getTransport() == null) {
      configBuilder.withTransport(new OkHttpTransport());
    }
    if (configBuilder.getDefaultRequestTimeout() == null) {
      configBuilder.withDefaultRequestTimeout(DEFAULT_DEFAULT_REQUEST_TIMEOUT);
    }
    if (configBuilder.getBaseUrl() == null || configBuilder.getBaseUrl().isEmpty()) {
      configBuilder.withBaseUrl(DEFAULT_BASE_URL);
    }
    if (configBuilder.getLocalAppName() == null || configBuilder.getLocalAppName().isEmpty()) {
      configBuilder.withLocalAppName(localAppName);
    }
    if (configBuilder.getLocalAppVersion() == null
        || configBuilder.getLocalAppVersion().isEmpty()) {
      configBuilder.withLocalAppVersion(localAppVersion);
    }

    final StitchAppClientConfiguration config = configBuilder.build();
    if (appClients.containsKey(config.getClientAppId())) {
      return appClients.get(config.getClientAppId());
    }

    final StitchAppClientImpl client = new StitchAppClientImpl(configBuilder.build());
    appClients.put(config.getClientAppId(), client);
    return client;
  }
}
