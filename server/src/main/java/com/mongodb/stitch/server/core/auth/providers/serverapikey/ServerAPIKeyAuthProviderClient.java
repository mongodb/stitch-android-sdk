package com.mongodb.stitch.server.core.auth.providers.serverapikey;

import com.mongodb.stitch.core.auth.providers.serverapikey.ServerAPIKeyCredential;

public interface ServerAPIKeyAuthProviderClient {
  ServerAPIKeyCredential getCredential(final String key);
}
