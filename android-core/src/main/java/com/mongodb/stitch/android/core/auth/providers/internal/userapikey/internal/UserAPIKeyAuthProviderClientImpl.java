package com.mongodb.stitch.android.core.auth.providers.internal.userapikey.internal;

import com.mongodb.stitch.android.core.auth.providers.internal.userapikey.UserAPIKeyAuthProviderClient;
import com.mongodb.stitch.core.auth.providers.userapikey.CoreUserAPIKeyAuthProviderClient;

public final class UserAPIKeyAuthProviderClientImpl extends CoreUserAPIKeyAuthProviderClient
    implements UserAPIKeyAuthProviderClient {

  public UserAPIKeyAuthProviderClientImpl() {
    super(CoreUserAPIKeyAuthProviderClient.DEFAULT_PROVIDER_NAME);
  }
}
