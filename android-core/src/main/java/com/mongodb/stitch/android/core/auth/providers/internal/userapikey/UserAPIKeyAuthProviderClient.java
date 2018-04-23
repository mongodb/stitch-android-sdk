package com.mongodb.stitch.android.core.auth.providers.internal.userapikey;

import com.mongodb.stitch.core.auth.providers.userapikey.UserAPIKeyCredential;

public interface UserAPIKeyAuthProviderClient {
  UserAPIKeyCredential getCredential(final String key);
}
