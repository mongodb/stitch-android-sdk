package com.mongodb.stitch.android.core.auth.providers.internal.custom;

import com.mongodb.stitch.core.auth.providers.custom.CustomCredential;

public interface CustomAuthProviderClient {
  CustomCredential getCredential(final String token);
}
