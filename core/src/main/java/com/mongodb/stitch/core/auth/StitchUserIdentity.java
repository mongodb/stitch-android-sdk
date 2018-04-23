package com.mongodb.stitch.core.auth;

public class StitchUserIdentity {
  protected final String id;
  protected final String providerType;

  protected StitchUserIdentity(final String id, final String providerType) {
    this.id = id;
    this.providerType = providerType;
  }

  protected StitchUserIdentity(final StitchUserIdentity identity) {
    this.id = identity.id;
    this.providerType = identity.providerType;
  }

  public String getId() {
    return id;
  }

  public String getProviderType() {
    return providerType;
  }
}
