package com.mongodb.stitch.server.core.auth.providers.userapikey;

import com.mongodb.stitch.core.auth.providers.userapikey.UserAPIKeyCredential;

public interface UserAPIKeyAuthProviderClient {
  UserAPIKeyCredential getCredential(final String key);
}
