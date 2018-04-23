package com.mongodb.stitch.server.core.auth.providers.anonymous;

import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential;

public interface AnonymousAuthProviderClient {
  AnonymousCredential getCredential();
}
