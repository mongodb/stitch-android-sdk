package com.mongodb.stitch.android.core.auth.providers.internal.google;

import com.mongodb.stitch.core.auth.providers.google.GoogleCredential;

public interface GoogleAuthProviderClient {
  GoogleCredential getCredential(final String authCode);
}
