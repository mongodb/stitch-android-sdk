package com.mongodb.stitch.server.core.auth.providers.google;

import com.mongodb.stitch.core.auth.providers.google.GoogleCredential;

public interface GoogleAuthProviderClient {
  GoogleCredential getCredential(final String authCode);
}
