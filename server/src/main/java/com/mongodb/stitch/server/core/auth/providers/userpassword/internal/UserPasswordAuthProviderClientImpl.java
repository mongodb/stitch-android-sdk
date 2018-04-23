package com.mongodb.stitch.server.core.auth.providers.userpassword.internal;

import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes;
import com.mongodb.stitch.core.auth.providers.userpass.CoreUserPasswordAuthProviderClient;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;
import com.mongodb.stitch.server.core.auth.providers.userpassword.UserPasswordAuthProviderClient;

public final class UserPasswordAuthProviderClientImpl extends CoreUserPasswordAuthProviderClient
    implements UserPasswordAuthProviderClient {

  public UserPasswordAuthProviderClientImpl(
      final StitchRequestClient requestClient, final StitchAuthRoutes routes) {
    super(CoreUserPasswordAuthProviderClient.DEFAULT_PROVIDER_NAME, requestClient, routes);
  }

  public void registerWithEmail(final String email, final String password) {
    registerWithEmailInternal(email, password);
  }

  public void confirmUser(final String token, final String tokenId) {
    confirmUserInternal(token, tokenId);
  }

  public void resendConfirmationEmail(final String email) {
    resendConfirmationEmailInternal(email);
  }

  public void resetPassword(final String token, final String tokenId, final String password) {
    resetPasswordInternal(token, tokenId, password);
  }

  public void sendResetPasswordEmail(final String email) {
    sendResetPasswordEmailInternal(email);
  }
}
