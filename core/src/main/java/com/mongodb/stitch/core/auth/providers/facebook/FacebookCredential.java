package com.mongodb.stitch.core.auth.providers.facebook;

import static com.mongodb.stitch.core.auth.providers.facebook.CoreFacebookAuthProviderClient.PROVIDER_TYPE;

import com.mongodb.stitch.core.auth.ProviderCapabilities;
import com.mongodb.stitch.core.auth.StitchCredential;
import org.bson.Document;

public final class FacebookCredential implements StitchCredential {

  private final String providerName;
  private final String accessToken;

  FacebookCredential(final String providerName, final String accessToken) {
    this.providerName = providerName;
    this.accessToken = accessToken;
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
    return new Document(Fields.ACCESS_TOKEN, accessToken);
  }

  @Override
  public ProviderCapabilities getProviderCapabilities() {
    return new ProviderCapabilities(false);
  }

  private static class Fields {
    static final String ACCESS_TOKEN = "accessToken";
  }
}
