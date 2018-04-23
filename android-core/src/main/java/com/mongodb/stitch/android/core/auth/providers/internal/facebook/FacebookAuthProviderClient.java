package com.mongodb.stitch.android.core.auth.providers.internal.facebook;

import com.mongodb.stitch.core.auth.providers.facebook.FacebookCredential;

public interface FacebookAuthProviderClient {
  FacebookCredential getCredential(final String accessToken);
}
