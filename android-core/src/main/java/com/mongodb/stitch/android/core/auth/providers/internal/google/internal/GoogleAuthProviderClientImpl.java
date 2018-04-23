package com.mongodb.stitch.android.core.auth.providers.internal.google.internal;

import com.mongodb.stitch.android.core.auth.providers.internal.google.GoogleAuthProviderClient;
import com.mongodb.stitch.core.auth.providers.google.CoreGoogleAuthProviderClient;

public final class GoogleAuthProviderClientImpl extends CoreGoogleAuthProviderClient
    implements GoogleAuthProviderClient {

  public GoogleAuthProviderClientImpl() {
    super(CoreGoogleAuthProviderClient.DEFAULT_PROVIDER_NAME);
  }
}
