package com.mongodb.stitch.server.core.auth.providers.serverapikey.internal;

import com.mongodb.stitch.core.auth.providers.serverapikey.CoreServerAPIKeyAuthProviderClient;
import com.mongodb.stitch.server.core.auth.providers.serverapikey.ServerAPIKeyAuthProviderClient;

public final class ServerAPIKeyAuthProviderClientImpl extends CoreServerAPIKeyAuthProviderClient
    implements ServerAPIKeyAuthProviderClient {

  public ServerAPIKeyAuthProviderClientImpl() {
    super(CoreServerAPIKeyAuthProviderClient.DEFAULT_PROVIDER_NAME);
  }
}
