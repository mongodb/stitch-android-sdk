package com.mongodb.stitch.android.core.auth.providers.internal.serverapikey.internal;

import com.mongodb.stitch.android.core.auth.providers.internal.serverapikey.ServerAPIKeyAuthProviderClient;
import com.mongodb.stitch.core.auth.providers.serverapikey.CoreServerAPIKeyAuthProviderClient;

public final class ServerAPIKeyAuthProviderClientImpl extends CoreServerAPIKeyAuthProviderClient
    implements ServerAPIKeyAuthProviderClient {

  public ServerAPIKeyAuthProviderClientImpl() {
    super(CoreServerAPIKeyAuthProviderClient.DEFAULT_PROVIDER_NAME);
  }
}
