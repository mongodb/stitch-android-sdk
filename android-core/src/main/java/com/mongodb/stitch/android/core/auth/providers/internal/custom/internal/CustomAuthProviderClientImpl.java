package com.mongodb.stitch.android.core.auth.providers.internal.custom.internal;

import com.mongodb.stitch.android.core.auth.providers.internal.custom.CustomAuthProviderClient;
import com.mongodb.stitch.core.auth.providers.custom.CoreCustomAuthProviderClient;

public final class CustomAuthProviderClientImpl extends CoreCustomAuthProviderClient
    implements CustomAuthProviderClient {

  public CustomAuthProviderClientImpl() {
    super(CoreCustomAuthProviderClient.DEFAULT_PROVIDER_NAME);
  }
}
