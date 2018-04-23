package com.mongodb.stitch.server.core.auth.providers.google.internal;

import com.mongodb.stitch.core.auth.providers.google.CoreGoogleAuthProviderClient;
import com.mongodb.stitch.server.core.auth.providers.google.GoogleAuthProviderClient;

public final class GoogleAuthProviderClientImpl extends CoreGoogleAuthProviderClient
    implements GoogleAuthProviderClient {

  public GoogleAuthProviderClientImpl() {
    super(CoreGoogleAuthProviderClient.DEFAULT_PROVIDER_NAME);
  }
}
