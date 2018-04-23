package com.mongodb.stitch.core.auth.providers.google;

public abstract class CoreGoogleAuthProviderClient {

  public static final String DEFAULT_PROVIDER_NAME = "oauth2-google";
  static final String PROVIDER_TYPE = "oauth2-google";
  private final String providerName;

  @SuppressWarnings("unused")
  protected CoreGoogleAuthProviderClient(final String providerName) {
    this.providerName = providerName;
  }

  @SuppressWarnings("unused")
  public GoogleCredential getCredential(final String authCode) {
    return new GoogleCredential(providerName, authCode);
  }
}
