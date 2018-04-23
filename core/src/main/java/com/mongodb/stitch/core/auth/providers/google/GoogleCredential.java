package com.mongodb.stitch.core.auth.providers.google;

import static com.mongodb.stitch.core.auth.providers.google.CoreGoogleAuthProviderClient.PROVIDER_TYPE;

import com.mongodb.stitch.core.auth.ProviderCapabilities;
import com.mongodb.stitch.core.auth.StitchCredential;
import org.bson.Document;

public final class GoogleCredential implements StitchCredential {

  private final String providerName;
  private final String authCode;

  GoogleCredential(final String providerName, final String authCode) {
    this.providerName = providerName;
    this.authCode = authCode;
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
    return new Document(Fields.AUTH_CODE, authCode);
  }

  @Override
  public ProviderCapabilities getProviderCapabilities() {
    return new ProviderCapabilities(false);
  }

  private static class Fields {
    static final String AUTH_CODE = "authCode";
  }
}
