package com.mongodb.stitch.core.auth.providers.anonymous;

public class CoreAnonymousAuthProviderClient {

  public static final String DEFAULT_PROVIDER_NAME = "anon-user";
  static final String PROVIDER_TYPE = "anon-user";
  private final String providerName;

  public CoreAnonymousAuthProviderClient() {
    this.providerName = DEFAULT_PROVIDER_NAME;
  }

  public CoreAnonymousAuthProviderClient(final String providerName) {
    this.providerName = providerName;
  }

  @SuppressWarnings("unused")
  public AnonymousCredential getCredential() {
    return new AnonymousCredential(providerName);
  }
}
