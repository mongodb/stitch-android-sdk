package com.mongodb.stitch.server.core.auth.providers.anonymous.internal;

import com.mongodb.stitch.core.auth.providers.anonymous.CoreAnonymousAuthProviderClient;
import com.mongodb.stitch.server.core.auth.providers.anonymous.AnonymousAuthProviderClient;

public final class AnonymousAuthProviderClientImpl extends CoreAnonymousAuthProviderClient
    implements AnonymousAuthProviderClient {

  public AnonymousAuthProviderClientImpl() {
    super(CoreAnonymousAuthProviderClient.DEFAULT_PROVIDER_NAME);
  }
}
