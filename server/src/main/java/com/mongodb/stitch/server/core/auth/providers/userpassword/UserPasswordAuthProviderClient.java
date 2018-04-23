package com.mongodb.stitch.server.core.auth.providers.userpassword;

import com.mongodb.stitch.core.auth.providers.userpass.UserPasswordCredential;

public interface UserPasswordAuthProviderClient {
  UserPasswordCredential getCredential(final String username, final String password);

  void registerWithEmail(final String email, final String password);

  void confirmUser(final String token, final String tokenId);

  void resendConfirmationEmail(final String email);

  void resetPassword(final String token, final String tokenId, final String password);

  void sendResetPasswordEmail(final String email);
}
