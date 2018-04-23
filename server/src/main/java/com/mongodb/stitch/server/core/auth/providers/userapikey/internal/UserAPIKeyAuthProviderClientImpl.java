package com.mongodb.stitch.server.core.auth.providers.userapikey.internal;

import com.mongodb.stitch.core.auth.providers.userapikey.CoreUserAPIKeyAuthProviderClient;
import com.mongodb.stitch.server.core.auth.providers.userapikey.UserAPIKeyAuthProviderClient;

public final class UserAPIKeyAuthProviderClientImpl extends CoreUserAPIKeyAuthProviderClient
    implements UserAPIKeyAuthProviderClient {

  public UserAPIKeyAuthProviderClientImpl() {
    super(CoreUserAPIKeyAuthProviderClient.DEFAULT_PROVIDER_NAME);
  }
}
