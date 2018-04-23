package com.mongodb.stitch.server.core.auth.providers.facebook;

import com.mongodb.stitch.core.auth.providers.facebook.FacebookCredential;

public interface FacebookAuthProviderClient {
  FacebookCredential getCredential(final String accessToken);
}
