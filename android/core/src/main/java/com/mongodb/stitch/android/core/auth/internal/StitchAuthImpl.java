/*
 * Copyright 2018-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.stitch.android.core.auth.internal;

import android.os.Build;
import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.Stitch;
import com.mongodb.stitch.android.core.auth.StitchAuth;
import com.mongodb.stitch.android.core.auth.StitchAuthListener;
import com.mongodb.stitch.android.core.auth.StitchUser;
import com.mongodb.stitch.android.core.auth.providers.internal.AuthProviderClientFactory;
import com.mongodb.stitch.android.core.auth.providers.internal.NamedAuthProviderClientFactory;
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

/**
 * The Android specific authentication component for clients that acts as a {@link StitchAuth} and
 * an {@link com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient}.
 */
public final class StitchAuthImpl extends CoreStitchAuth<StitchUser> implements StitchAuth {
  private final TaskDispatcher dispatcher;
  private final StitchAppClientInfo appInfo;
  private final Set<StitchAuthListener> listeners = new HashSet<>();

  /**
   * Constructs a {@link StitchAuthImpl}.
   *
   * @param requestClient The request client to use for any Stitch requests.
   * @param authRoutes Auth specific routes.
   * @param storage Where to store/retrieve authentication data.
   * @param dispatcher Where to send asynchronous requests to.
   * @param appInfo Information about the application.
   */
  public StitchAuthImpl(
      final StitchRequestClient requestClient,
      final StitchAuthRoutes authRoutes,
      final Storage storage,
      final TaskDispatcher dispatcher,
      final StitchAppClientInfo appInfo) {
    super(requestClient, authRoutes, storage, appInfo.getConfiguredCodecRegistry(), true);
    this.dispatcher = dispatcher;
    this.appInfo = appInfo;
  }

  protected StitchUserFactory<StitchUser> getUserFactory() {
    return new StitchUserFactoryImpl(this);
  }

  @Override
  public <T> T getProviderClient(final AuthProviderClientFactory<T> provider) {
    return provider.getClient(getRequestClient(), getAuthRoutes(), dispatcher);
  }

  @Override
  public <T> T getProviderClient(
      final NamedAuthProviderClientFactory<T> provider, final String providerName) {
    return provider.getClient(providerName, getRequestClient(), getAuthRoutes(), dispatcher);
  }

  @Override
  public Task<StitchUser> loginWithCredential(final StitchCredential credential) {
    return dispatcher.dispatchTask(
        new Callable<StitchUser>() {
          @Override
          public StitchUser call() throws Exception {
            return loginWithCredentialInternal(credential);
          }
        });
  }

  Task<StitchUser> linkWithCredential(
      final CoreStitchUser user, final StitchCredential credential) {
    return dispatcher.dispatchTask(
        new Callable<StitchUser>() {
          @Override
          public StitchUser call() throws Exception {
            return linkUserWithCredentialInternal(user, credential);
          }
        });
  }

  @Override
  public Task<Void> logout() {
    return dispatcher.dispatchTask(
        new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            logoutInternal();
            return null;
          }
        });
  }

  @Override
  protected Document getDeviceInfo() {
    final Document info = super.getDeviceInfo();
    if (appInfo.getLocalAppName() != null) {
      info.put(DeviceFields.APP_ID, appInfo.getLocalAppName());
    }
    if (appInfo.getLocalAppVersion() != null) {
      info.put(DeviceFields.APP_VERSION, appInfo.getLocalAppVersion());
    }
    info.put(DeviceFields.PLATFORM, "android");
    info.put(DeviceFields.PLATFORM_VERSION, Build.VERSION.RELEASE);

    final String packageVersion = Stitch.class.getPackage().getImplementationVersion();
    if (packageVersion != null && !packageVersion.isEmpty()) {
      info.put(DeviceFields.SDK_VERSION, packageVersion);
    }

    return info;
  }

  /**
   * Adds a listener for any important auth event.
   *
   * @see StitchAuthListener
   */
  public void addAuthListener(final StitchAuthListener listener) {
    synchronized (this) {
      listeners.add(listener);
    }

    // Trigger the onUserLoggedIn event in case some event happens and
    // this caller would miss out on this event other wise.
    onAuthEvent(listener);
  }

  /**
   * Removes a listener.
   *
   * @see StitchAuthListener
   */
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
  }
}
