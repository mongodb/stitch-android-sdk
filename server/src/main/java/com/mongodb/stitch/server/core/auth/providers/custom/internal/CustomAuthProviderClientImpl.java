package com.mongodb.stitch.server.core.auth.providers.custom.internal;

import com.mongodb.stitch.core.auth.providers.custom.CoreCustomAuthProviderClient;
import com.mongodb.stitch.server.core.auth.providers.custom.CustomAuthProviderClient;

public final class CustomAuthProviderClientImpl extends CoreCustomAuthProviderClient
    implements CustomAuthProviderClient {

  public CustomAuthProviderClientImpl() {
    super(CoreCustomAuthProviderClient.DEFAULT_PROVIDER_NAME);
  }
}
