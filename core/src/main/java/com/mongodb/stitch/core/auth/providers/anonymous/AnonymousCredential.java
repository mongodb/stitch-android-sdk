package com.mongodb.stitch.core.auth.providers.anonymous;

import static com.mongodb.stitch.core.auth.providers.anonymous.CoreAnonymousAuthProviderClient.PROVIDER_TYPE;

import com.mongodb.stitch.core.auth.ProviderCapabilities;
import com.mongodb.stitch.core.auth.StitchCredential;
import org.bson.Document;

public final class AnonymousCredential implements StitchCredential {

  private final String providerName;

  AnonymousCredential(final String providerName) {
    this.providerName = providerName;
  }

  @Override
  public String getProviderName() {
    return providerName;
  }

  @Override
  public String getProviderType() {
    return PROVIDER_TYPE;
  }

  @Override
  public Document getMaterial() {
    return null;
  }

  @Override
  public ProviderCapabilities getProviderCapabilities() {
    return new ProviderCapabilities(true);
  }
}
