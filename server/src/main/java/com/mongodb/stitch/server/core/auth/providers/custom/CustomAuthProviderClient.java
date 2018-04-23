package com.mongodb.stitch.server.core.auth.providers.custom;

import com.mongodb.stitch.core.auth.providers.custom.CustomCredential;

public interface CustomAuthProviderClient {
  CustomCredential getCredential(final String token);
}
