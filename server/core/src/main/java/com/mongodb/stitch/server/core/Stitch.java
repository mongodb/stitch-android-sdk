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

package com.mongodb.stitch.server.core;

import com.mongodb.stitch.core.StitchAppClientConfiguration;
import com.mongodb.stitch.core.internal.common.MemoryStorage;
import com.mongodb.stitch.core.internal.net.OkHttpTransport;
import com.mongodb.stitch.server.core.internal.StitchAppClientImpl;

import java.util.HashMap;
import java.util.Map;

/**
 * The entry point into the Server SDK. This class is responsible for allowing one to initialize
 * {@link StitchAppClient}s and work with Stitch.
 */
public final class Stitch {
  private static final String DEFAULT_BASE_URL = "https://stitch.mongodb.com";
  private static final Long DEFAULT_DEFAULT_REQUEST_TIMEOUT = 15000L;
  private static final Map<String, StitchAppClient> appClients = new HashMap<>();
  private static boolean initialized;
  private static String defaultClientAppId;

  /** Initializes the Stitch SDK so that app clients can be created. */
  public static synchronized void initialize() {
    if (initialized) {
      return;
    }

    initialized = true;
    System.out.println("Initialized Stitch SDK");
  }

  private static synchronized void ensureInitialized() {
    if (!initialized) {
      throw new IllegalStateException("Stitch not initialized yet; please call initialize() first");
    }
  }

  /**
   * Gets the default initialized app client. If one has not been set, then an error will be thrown.
   * {@link Stitch#initializeDefaultAppClient(StitchAppClientConfiguration.Builder)} should be used
   * to initialize the default app client.
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
  public static synchronized StitchAppClient getAppClient(final String clientAppId) {
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
   * @param configBuilder the configuration to use to build the app client.
   * @return the app client that was just initialized.
   */
  public static synchronized StitchAppClient initializeDefaultAppClient(
      final StitchAppClientConfiguration.Builder configBuilder) {
    ensureInitialized();
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
   * @param configBuilder the configuration to use to build the app client.
   * @return the app client that was just initialized.
   */
  public static synchronized StitchAppClient initializeAppClient(
      final StitchAppClientConfiguration.Builder configBuilder) {
    ensureInitialized();
    final String clientAppId = configBuilder.getClientAppId();
    if (clientAppId == null || clientAppId.isEmpty()) {
      throw new IllegalArgumentException("clientAppId must be set to a non-empty string");
    }

    if (appClients.containsKey(clientAppId)) {
      throw new IllegalStateException(
          String.format("client for app '%s' has already been initialized", clientAppId));
    }

    if (configBuilder.getStorage() == null) {
      configBuilder.withStorage(new MemoryStorage());
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

    final StitchAppClientConfiguration config = configBuilder.build();
    if (appClients.containsKey(config.getClientAppId())) {
      return appClients.get(config.getClientAppId());
    }

    final StitchAppClientImpl client = new StitchAppClientImpl(configBuilder.build());
    appClients.put(config.getClientAppId(), client);
    return client;
  }
}
