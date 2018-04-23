package com.mongodb.stitch.core.auth.providers.userpass;

import static com.mongodb.stitch.core.auth.providers.userpass.CoreUserPasswordAuthProviderClient.PROVIDER_TYPE;

import com.mongodb.stitch.core.auth.ProviderCapabilities;
import com.mongodb.stitch.core.auth.StitchCredential;
import org.bson.Document;

public final class UserPasswordCredential implements StitchCredential {

  private final String providerName;
  private final String username;
  private final String password;

  public UserPasswordCredential(
      final String providerName, final String username, final String password) {
    this.providerName = providerName;
    this.username = username;
    this.password = password;
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
    return new Document(Fields.USERNAME, username).append(Fields.PASSWORD, password);
  }

  @Override
  public ProviderCapabilities getProviderCapabilities() {
    return new ProviderCapabilities(false);
  }

  private static class Fields {
    static final String USERNAME = "username";
    static final String PASSWORD = "password";
  }
}
