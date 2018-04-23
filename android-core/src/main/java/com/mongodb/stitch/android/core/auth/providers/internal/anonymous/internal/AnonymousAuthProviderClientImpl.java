package com.mongodb.stitch.android.core.auth.providers.internal.anonymous.internal;

import com.mongodb.stitch.android.core.auth.providers.internal.anonymous.AnonymousAuthProviderClient;
import com.mongodb.stitch.core.auth.providers.anonymous.CoreAnonymousAuthProviderClient;

public final class AnonymousAuthProviderClientImpl extends CoreAnonymousAuthProviderClient
    implements AnonymousAuthProviderClient {

  public AnonymousAuthProviderClientImpl() {
    super(CoreAnonymousAuthProviderClient.DEFAULT_PROVIDER_NAME);
  }
}
