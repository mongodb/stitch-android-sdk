package com.mongodb.stitch.core.auth.providers.userapikey;

public abstract class CoreUserAPIKeyAuthProviderClient {

  public static final String DEFAULT_PROVIDER_NAME = "api-key";
  static final String PROVIDER_TYPE = "api-key";
  private final String providerName;

  protected CoreUserAPIKeyAuthProviderClient(final String providerName) {
    this.providerName = providerName;
  }

  public UserAPIKeyCredential getCredential(final String key) {
    return new UserAPIKeyCredential(providerName, key);
  }
}
