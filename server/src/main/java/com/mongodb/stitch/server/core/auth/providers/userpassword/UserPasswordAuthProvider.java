package com.mongodb.stitch.server.core.auth.providers.userpassword;

import com.mongodb.stitch.server.core.auth.providers.AuthProviderClientSupplier;
import com.mongodb.stitch.server.core.auth.providers.userpassword.internal.UserPasswordAuthProviderClientImpl;

public final class UserPasswordAuthProvider {
  public static final AuthProviderClientSupplier<UserPasswordAuthProviderClient> ClientProvider =
      UserPasswordAuthProviderClientImpl::new;
}
