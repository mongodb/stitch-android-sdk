package com.mongodb.stitch.core.auth.providers.custom;

public abstract class CoreCustomAuthProviderClient {

  public static final String DEFAULT_PROVIDER_NAME = "custom-token";
  static final String PROVIDER_TYPE = "custom-token";
  private final String providerName;

  @SuppressWarnings("unused")
  protected CoreCustomAuthProviderClient(final String providerName) {
    this.providerName = providerName;
  }

  @SuppressWarnings("unused")
  public CustomCredential getCredential(final String token) {
    return new CustomCredential(providerName, token);
  }
}
