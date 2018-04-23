package com.mongodb.stitch.android.core.auth.providers.internal.anonymous;

import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential;

public interface AnonymousAuthProviderClient {
  AnonymousCredential getCredential();
}
