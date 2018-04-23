package com.mongodb.stitch.server.core;

import com.mongodb.stitch.core.StitchAppClientConfiguration;
import com.mongodb.stitch.core.internal.common.BSONUtils;
import com.mongodb.stitch.core.internal.common.MemoryStorage;
import com.mongodb.stitch.core.internal.net.OkHttpTransport;
import com.mongodb.stitch.server.core.internal.StitchAppClientImpl;
import java.util.HashMap;
import java.util.Map;

public final class Stitch {
  private static final String DEFAULT_BASE_URL = "https://stitch.mongodb.com";
  private static final Map<String, StitchAppClient> appClients = new HashMap<>();
  private static boolean initialized;
  private static String defaultClientAppId;

  public static synchronized void initialize() {
    if (initialized) {
      return;
    }

    initialized = true;
    System.out.println("Initialized Stitch SDK");
  }

  public static synchronized StitchAppClient getDefaultAppClient() {
    if (defaultClientAppId == null) {
      throw new IllegalStateException("default app client has not yet been initialized/set");
    }
    return appClients.get(defaultClientAppId);
  }

  public static synchronized StitchAppClient getAppClient(final String clientAppId) {
    if (!appClients.containsKey(clientAppId)) {
      throw new IllegalStateException(
          String.format("client for app '%s' has not yet been initialized", clientAppId));
    }
    return appClients.get(clientAppId);
  }

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

    if (configBuilder.getStorage() == null) {
      configBuilder.withStorage(new MemoryStorage());
    }
    if (configBuilder.getTransport() == null) {
      configBuilder.withTransport(new OkHttpTransport());
    }
    if (configBuilder.getBaseURL() == null || configBuilder.getBaseURL().isEmpty()) {
      configBuilder.withBaseURL(DEFAULT_BASE_URL);
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
