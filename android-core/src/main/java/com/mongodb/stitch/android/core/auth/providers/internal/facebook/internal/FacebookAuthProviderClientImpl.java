package com.mongodb.stitch.android.core.auth.providers.internal.facebook.internal;

import com.mongodb.stitch.android.core.auth.providers.internal.facebook.FacebookAuthProviderClient;
import com.mongodb.stitch.core.auth.providers.facebook.CoreFacebookAuthProviderClient;

public final class FacebookAuthProviderClientImpl extends CoreFacebookAuthProviderClient
    implements FacebookAuthProviderClient {

  public FacebookAuthProviderClientImpl() {
    super(CoreFacebookAuthProviderClient.DEFAULT_PROVIDER_NAME);
  }
}
