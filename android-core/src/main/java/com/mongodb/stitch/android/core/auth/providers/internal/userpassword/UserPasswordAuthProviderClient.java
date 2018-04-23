package com.mongodb.stitch.android.core.auth.providers.internal.userpassword;

import android.support.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.core.auth.providers.userpass.UserPasswordCredential;

public interface UserPasswordAuthProviderClient {
  UserPasswordCredential getCredential(
      @NonNull final String username, @NonNull final String password);

  Task<Void> registerWithEmail(@NonNull final String email, @NonNull final String password);

  Task<Void> confirmUser(@NonNull final String token, @NonNull final String tokenId);

  Task<Void> resendConfirmationEmail(@NonNull final String email);

  Task<Void> resetPassword(
      @NonNull final String token, @NonNull final String tokenId, @NonNull final String password);

  Task<Void> sendResetPasswordEmail(@NonNull final String email);
}
