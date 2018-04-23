package com.mongodb.stitch.core.auth.providers.serverapikey;

public abstract class CoreServerAPIKeyAuthProviderClient {

  public static final String DEFAULT_PROVIDER_NAME = "api-key";
  static final String PROVIDER_TYPE = "api-key";
  private final String providerName;

  @SuppressWarnings("unused")
  protected CoreServerAPIKeyAuthProviderClient(final String providerName) {
    this.providerName = providerName;
  }

  @SuppressWarnings("unused")
  public ServerAPIKeyCredential getCredential(final String key) {
    return new ServerAPIKeyCredential(providerName, key);
  }
}
