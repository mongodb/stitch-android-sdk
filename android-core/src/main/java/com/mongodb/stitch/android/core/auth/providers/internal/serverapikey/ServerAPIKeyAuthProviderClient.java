package com.mongodb.stitch.android.core.auth.providers.internal.serverapikey;

import com.mongodb.stitch.core.auth.providers.serverapikey.ServerAPIKeyCredential;

public interface ServerAPIKeyAuthProviderClient {
  ServerAPIKeyCredential getCredential(final String key);
}
