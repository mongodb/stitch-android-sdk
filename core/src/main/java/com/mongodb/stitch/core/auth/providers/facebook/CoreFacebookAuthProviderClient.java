package com.mongodb.stitch.core.auth.providers.facebook;

public abstract class CoreFacebookAuthProviderClient {

  public static final String DEFAULT_PROVIDER_NAME = "oauth2-facebook";
  static final String PROVIDER_TYPE = "oauth2-facebook";
  private final String providerName;

  @SuppressWarnings("unused")
  protected CoreFacebookAuthProviderClient(final String providerName) {
    this.providerName = providerName;
  }

  @SuppressWarnings("unused")
  public FacebookCredential getCredential(final String accessToken) {
    return new FacebookCredential(providerName, accessToken);
  }
}
