package com.mongodb.stitch.core.auth.providers.function;

import com.mongodb.stitch.core.auth.ProviderCapabilities;
import com.mongodb.stitch.core.auth.StitchCredential;

import org.bson.Document;

public class FunctionCredential implements StitchCredential {
  private final String providerName;
  private final Document payload;

  /**
   * Constructs a Function credential for a user.
   *
   * @param payload arguments to be passed to a custom function.
   * @see <a href="https://docs.mongodb.com/stitch/auth/custom-function-auth/">Function Authentication</a>
   */
  public FunctionCredential(final Document payload) {
    this(FunctionAuthProvider.DEFAULT_NAME, payload);
  }

  private FunctionCredential(final String providerName, final Document payload) {
    this.payload = payload;
    this.providerName = providerName;
  }

  @Override
  public String getProviderName() {
    return providerName;
  }

  @Override
  public String getProviderType() {
    return FunctionAuthProvider.TYPE;
  }

  @Override
  public Document getMaterial() {
    return payload;
  }

  @Override
  public ProviderCapabilities getProviderCapabilities() {
    return new ProviderCapabilities(false);
  }
}
