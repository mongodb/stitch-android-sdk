package com.mongodb.stitch.server.core.auth.providers.facebook.internal;

import com.mongodb.stitch.core.auth.providers.facebook.CoreFacebookAuthProviderClient;
import com.mongodb.stitch.server.core.auth.providers.facebook.FacebookAuthProviderClient;

public final class FacebookAuthProviderClientImpl extends CoreFacebookAuthProviderClient
    implements FacebookAuthProviderClient {

  public FacebookAuthProviderClientImpl() {
    super(CoreFacebookAuthProviderClient.DEFAULT_PROVIDER_NAME);
  }
}
