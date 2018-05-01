package com.mongodb.stitch.android.core.auth.internal;

import android.os.Build;
import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.Stitch;
import com.mongodb.stitch.android.core.auth.StitchAuth;
import com.mongodb.stitch.android.core.auth.StitchAuthListener;
import com.mongodb.stitch.android.core.auth.StitchUser;
import com.mongodb.stitch.android.core.auth.providers.internal.AuthProviderClientSupplier;
import com.mongodb.stitch.android.core.auth.providers.internal.NamedAuthProviderClientSupplier;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.core.StitchAppClientInfo;
import com.mongodb.stitch.core.auth.StitchCredential;
import com.mongodb.stitch.core.auth.internal.CoreStitchAuth;
import com.mongodb.stitch.core.auth.internal.CoreStitchUser;
import com.mongodb.stitch.core.auth.internal.DeviceFields;
import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes;
import com.mongodb.stitch.core.auth.internal.StitchUserFactory;
import com.mongodb.stitch.core.internal.common.Storage;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import org.bson.Document;

public final class StitchAuthImpl extends CoreStitchAuth<StitchUser> implements StitchAuth {
  private final TaskDispatcher dispatcher;
  private final StitchAppClientInfo appInfo;
  private final Set<StitchAuthListener> listeners = new HashSet<>();

  public StitchAuthImpl(
      final StitchRequestClient requestClient,
      final StitchAuthRoutes authRoutes,
      final Storage storage,
      final TaskDispatcher dispatcher,
      final StitchAppClientInfo appInfo) {
    super(requestClient, authRoutes, storage, appInfo.configuredCodecRegistry, true);
    this.dispatcher = dispatcher;
    this.appInfo = appInfo;
  }

  protected StitchUserFactory<StitchUser> getUserFactory() {
    return new StitchUserFactoryImpl(this);
  }

  @Override
  public <T> T getProviderClient(final AuthProviderClientSupplier<T> provider) {
    return provider.getClient(requestClient, authRoutes, dispatcher);
  }

  @Override
  public <T> T getProviderClient(
      final NamedAuthProviderClientSupplier<T> provider, final String providerName) {
    return provider.getClient(providerName, requestClient, authRoutes, dispatcher);
  }

  @Override
  public Task<StitchUser> loginWithCredential(final StitchCredential credential) {
    return dispatcher.dispatchTask(
        new Callable<StitchUser>() {
          @Override
          public StitchUser call() throws Exception {
            return loginWithCredentialBlocking(credential);
          }
        });
  }

  Task<StitchUser> linkWithCredential(
      final CoreStitchUser user, final StitchCredential credential) {
    return dispatcher.dispatchTask(
        new Callable<StitchUser>() {
          @Override
          public StitchUser call() throws Exception {
            return linkUserWithCredentialBlocking(user, credential);
          }
        });
  }

  @Override
  public Task<Void> logout() {
    return dispatcher.dispatchTask(
        new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            logoutBlocking();
            return null;
          }
        });
  }

  @Override
  protected Document getDeviceInfo() {
    final Document info = new Document();
    if (hasDeviceId()) {
      info.put(DeviceFields.DEVICE_ID, getDeviceId());
    }
    if (appInfo.localAppName != null) {
      info.put(DeviceFields.APP_ID, appInfo.localAppName);
    }
    if (appInfo.localAppVersion != null) {
      info.put(DeviceFields.APP_VERSION, appInfo.localAppVersion);
    }
    info.put(DeviceFields.PLATFORM, "android");
    info.put(DeviceFields.PLATFORM_VERSION, Build.VERSION.RELEASE);

    final String packageVersion = Stitch.class.getPackage().getImplementationVersion();
    if (packageVersion != null && !packageVersion.isEmpty()) {
      info.put(DeviceFields.SDK_VERSION, packageVersion);
    }

    return info;
  }

  public void addAuthListener(final StitchAuthListener listener) {
    synchronized (this) {
      listeners.add(listener);
    }

    // Trigger the onUserLoggedIn event in case some event happens and
    // this caller would miss out on this event other wise.
    onAuthEvent(listener);
  }

  public synchronized void removeAuthListener(final StitchAuthListener listener) {
    listeners.remove(listener);
  }

  private void onAuthEvent(final StitchAuthListener listener) {
    final StitchAuth auth = this;
    dispatcher.dispatchTask(
        new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            listener.onAuthEvent(auth);
            return null;
          }
        });
  }

  @Override
  protected void onAuthEvent() {
    for (final StitchAuthListener listener : listeners) {
      onAuthEvent(listener);
    }
  }

  @Override
  public void close() throws IOException {
    super.close();
    dispatcher.close();
  }
}
