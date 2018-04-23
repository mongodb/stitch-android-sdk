package com.mongodb.stitch.core;

public final class StitchAppClientConfiguration extends StitchClientConfiguration {
  private final String clientAppId;
  private final String localAppName;
  private final String localAppVersion;

  private StitchAppClientConfiguration(
      final StitchClientConfiguration config,
      final String clientAppId,
      final String localAppName,
      final String localAppVersion) {
    super(config);
    this.clientAppId = clientAppId;
    this.localAppVersion = localAppVersion;
    this.localAppName = localAppName;
  }

  public Builder builder() {
    return new Builder(this);
  }

  public String getClientAppId() {
    return clientAppId;
  }

  public String getLocalAppName() {
    return localAppName;
  }

  public String getLocalAppVersion() {
    return localAppVersion;
  }

  public static class Builder extends StitchClientConfiguration.Builder {
    private String clientAppId;
    private String localAppName;
    private String localAppVersion;

    public Builder() {}

    private Builder(final StitchAppClientConfiguration config) {
      super(config);
      clientAppId = config.clientAppId;
      localAppVersion = config.localAppVersion;
      localAppName = config.localAppName;
    }

    public static Builder forApp(final String clientAppId) {
      return new Builder().withClientAppId(clientAppId);
    }

    public static Builder forApp(final String clientAppId, final String baseURL) {
      final Builder builder = new Builder();
      builder.withBaseURL(baseURL);
      return builder.withClientAppId(clientAppId);
    }

    public Builder withClientAppId(final String clientAppId) {
      this.clientAppId = clientAppId;
      return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    public Builder withLocalAppName(final String localAppName) {
      this.localAppName = localAppName;
      return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    public Builder withLocalAppVersion(final String localAppVersion) {
      this.localAppVersion = localAppVersion;
      return this;
    }

    public String getClientAppId() {
      return clientAppId;
    }

    public String getLocalAppName() {
      return localAppName;
    }

    public String getLocalAppVersion() {
      return localAppVersion;
    }

    public StitchAppClientConfiguration build() {
      if (clientAppId == null || clientAppId.isEmpty()) {
        throw new IllegalArgumentException("clientAppId must be set to a non-empty string");
      }

      final StitchClientConfiguration config = super.build();
      return new StitchAppClientConfiguration(config, clientAppId, localAppName, localAppVersion);
    }
  }
}
