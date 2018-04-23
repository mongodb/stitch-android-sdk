package com.mongodb.stitch.core.auth.providers.userapikey;

import static com.mongodb.stitch.core.auth.providers.userapikey.CoreUserAPIKeyAuthProviderClient.PROVIDER_TYPE;

import com.mongodb.stitch.core.auth.ProviderCapabilities;
import com.mongodb.stitch.core.auth.StitchCredential;
import org.bson.Document;

public final class UserAPIKeyCredential implements StitchCredential {

  private final String providerName;
  private final String key;

  UserAPIKeyCredential(final String providerName, final String key) {
    this.providerName = providerName;
    this.key = key;
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
    return new Document(Fields.KEY, key);
  }

  @Override
  public ProviderCapabilities getProviderCapabilities() {
    return new ProviderCapabilities(false);
  }

  private static class Fields {
    static final String KEY = "key";
  }
}
