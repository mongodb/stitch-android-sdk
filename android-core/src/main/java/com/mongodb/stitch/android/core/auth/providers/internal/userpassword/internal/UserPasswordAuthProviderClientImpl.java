package com.mongodb.stitch.android.core.auth.providers.internal.userpassword.internal;

import android.support.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.auth.providers.internal.userpassword.UserPasswordAuthProviderClient;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes;
import com.mongodb.stitch.core.auth.providers.userpass.CoreUserPasswordAuthProviderClient;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;
import java.util.concurrent.Callable;

public final class UserPasswordAuthProviderClientImpl extends CoreUserPasswordAuthProviderClient
    implements UserPasswordAuthProviderClient {

  private final TaskDispatcher dispatcher;

  public UserPasswordAuthProviderClientImpl(
      final StitchRequestClient requestClient,
      final StitchAuthRoutes routes,
      final TaskDispatcher dispatcher) {
    super(CoreUserPasswordAuthProviderClient.DEFAULT_PROVIDER_NAME, requestClient, routes);
    this.dispatcher = dispatcher;
  }

  public Task<Void> registerWithEmail(@NonNull final String email, @NonNull final String password) {
    return dispatcher.dispatchTask(
        new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            registerWithEmailInternal(email, password);
            return null;
          }
        });
  }

  public Task<Void> confirmUser(@NonNull final String token, @NonNull final String tokenId) {
    return dispatcher.dispatchTask(
        new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            confirmUserInternal(token, tokenId);
            return null;
          }
        });
  }

  public Task<Void> resendConfirmationEmail(@NonNull final String email) {
    return dispatcher.dispatchTask(
        new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            resendConfirmationEmailInternal(email);
            return null;
          }
        });
  }

  public Task<Void> resetPassword(
      @NonNull final String token, @NonNull final String tokenId, @NonNull final String password) {
    return dispatcher.dispatchTask(
        new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            resetPasswordInternal(token, tokenId, password);
            return null;
          }
        });
  }

  public Task<Void> sendResetPasswordEmail(@NonNull final String email) {
    return dispatcher.dispatchTask(
        new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            sendResetPasswordEmailInternal(email);
            return null;
          }
        });
  }
}
