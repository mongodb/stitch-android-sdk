package com.mongodb.stitch.core.auth.providers.custom;

import static com.mongodb.stitch.core.auth.providers.custom.CoreCustomAuthProviderClient.PROVIDER_TYPE;

import com.mongodb.stitch.core.auth.ProviderCapabilities;
import com.mongodb.stitch.core.auth.StitchCredential;
import org.bson.Document;

public final class CustomCredential implements StitchCredential {

  private final String providerName;
  private final String token;

  CustomCredential(final String providerName, final String token) {
    this.providerName = providerName;
    this.token = token;
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
    return new Document(Fields.TOKEN, token);
  }

  @Override
  public ProviderCapabilities getProviderCapabilities() {
    return new ProviderCapabilities(false);
  }

  private static class Fields {
    static final String TOKEN = "token";
  }
}
