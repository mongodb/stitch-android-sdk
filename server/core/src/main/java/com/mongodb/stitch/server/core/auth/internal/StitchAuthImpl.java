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

package com.mongodb.stitch.server.core.auth.internal;

import com.mongodb.stitch.core.StitchAppClientInfo;
import com.mongodb.stitch.core.auth.StitchCredential;
import com.mongodb.stitch.core.auth.internal.CoreStitchAuth;
import com.mongodb.stitch.core.auth.internal.CoreStitchUser;
import com.mongodb.stitch.core.auth.internal.DeviceFields;
import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes;
import com.mongodb.stitch.core.auth.internal.StitchUserFactory;
import com.mongodb.stitch.core.internal.common.Storage;
import com.mongodb.stitch.core.internal.common.ThreadDispatcher;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;
import com.mongodb.stitch.server.core.Stitch;
import com.mongodb.stitch.server.core.auth.StitchAuth;
import com.mongodb.stitch.server.core.auth.StitchAuthListener;
import com.mongodb.stitch.server.core.auth.StitchUser;
import com.mongodb.stitch.server.core.auth.providers.internal.AuthProviderClientFactory;
import com.mongodb.stitch.server.core.auth.providers.internal.NamedAuthProviderClientFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.annotation.Nullable;

import org.bson.Document;

public final class StitchAuthImpl extends CoreStitchAuth<StitchUser> implements StitchAuth {
  private final StitchAppClientInfo appInfo;
  private final ThreadDispatcher dispatcher;

  /**
   * A set of auth listeners that should be dispatched to asynchronously
   */
  private final Set<StitchAuthListener> listeners = new HashSet<>();

  public StitchAuthImpl(
      final StitchRequestClient requestClient,
      final StitchAuthRoutes authRoutes,
      final Storage storage,
      final StitchAppClientInfo appInfo) {
    super(requestClient, authRoutes, storage, true);
    this.appInfo = appInfo;
    this.dispatcher = new ThreadDispatcher();
  }

  protected StitchUserFactory<StitchUser> getUserFactory() {
    return new StitchUserFactoryImpl(this);
  }

  @Override
  public <ClientT> ClientT getProviderClient(
          final AuthProviderClientFactory<ClientT> factory) {
    return factory.getClient(this, getRequestClient(), getAuthRoutes());
  }

  @Override
  public <T> T getProviderClient(
      final NamedAuthProviderClientFactory<T> factory, final String providerName) {
    return factory.getClient(providerName, getRequestClient(), getAuthRoutes());
  }

  @Override
  public StitchUser loginWithCredential(final StitchCredential credential) {
    return loginWithCredentialInternal(credential);
  }

  StitchUser linkWithCredential(final CoreStitchUser user, final StitchCredential credential) {
    return linkUserWithCredentialInternal(user, credential);
  }

  @Override
  public void logout() {
    logoutInternal();
  }

  @Override
  public void logoutUserWithId(final String userId) {
    logoutUserWithIdInternal(userId);
  }

  @Override
  public void removeUser() {
    removeUserInternal();
  }

  @Override
  public void removeUserWithId(final String userId) {
    removeUserWithIdInternal(userId);
  }

  /**
   * Adds a listener for any important auth event.
   *
   * @see StitchAuthListener
   */
  @Override
  public void addAuthListener(final StitchAuthListener listener) {
    synchronized (this) {
      listeners.add(listener);
    }

    // Trigger the onUserLoggedIn event in case some event happens and
    // this caller would miss out on this event other wise.
    onAuthEvent(listener);
    dispatcher.dispatch(
        new Callable<Void>() {
          @Override
          public Void call() {
            listener.onListenerRegistered(StitchAuthImpl.this);
            return null;
          }
        });
  }

  /**
   * Removes a listener.
   *
   * @see StitchAuthListener
   */
  @Override
  public synchronized void removeAuthListener(final StitchAuthListener listener) {
    listeners.remove(listener);
  }

  private void onAuthEvent(final StitchAuthListener listener) {
    final StitchAuth auth = this;
    dispatcher.dispatch(
        new Callable<Void>() {
          @Override
          public Void call() {
            listener.onAuthEvent(auth);
            return null;
          }
        });
  }

  @Override
  protected void onAuthEvent() {
    for (final StitchAuthListener listener : listeners) {
      dispatcher.dispatch(
          new Callable<Void>() {
            @Override
            public Void call() {
              listener.onAuthEvent(StitchAuthImpl.this);
              return null;
            }
          });
    }
  }

  @Override
  protected void onListenerInitialized() {
    for (final StitchAuthListener listener : listeners) {
      dispatcher.dispatch(
          new Callable<Void>() {
            @Override
            public Void call() {
              listener.onListenerRegistered(StitchAuthImpl.this);
              return null;
            }
          });
    }
  }

  @Override
  protected void onActiveUserChanged(@Nullable final StitchUser currentActiveUser,
                                     @Nullable final StitchUser previousActiveUser) {
    for (final StitchAuthListener listener : listeners) {
      dispatcher.dispatch(
          new Callable<Void>() {
            @Override
            public Void call() {
              listener.onActiveUserChanged(
                  StitchAuthImpl.this, currentActiveUser, previousActiveUser);
              return null;
            }
          });
    }
  }

  @Override
  protected void onUserAdded(final StitchUser createdUser) {
    for (final StitchAuthListener listener : listeners) {
      dispatcher.dispatch(
          new Callable<Void>() {
            @Override
            public Void call() {
              listener.onUserAdded(
                  StitchAuthImpl.this, createdUser);
              return null;
            }
          });
    }
  }

  @Override
  protected void onUserLoggedIn(final StitchUser loggedInUser) {
    for (final StitchAuthListener listener : listeners) {
      dispatcher.dispatch(
          new Callable<Void>() {
            @Override
            public Void call() {
              listener.onUserLoggedIn(
                  StitchAuthImpl.this, loggedInUser);
              return null;
            }
          });
    }
  }

  @Override
  protected void onUserRemoved(final StitchUser removedUser) {
    for (final StitchAuthListener listener : listeners) {
      dispatcher.dispatch(
          new Callable<Void>() {
            @Override
            public Void call() {
              listener.onUserRemoved(StitchAuthImpl.this, removedUser);
              return null;
            }
          });
    }
  }

  @Override
  protected void onUserLoggedOut(final StitchUser loggedOutUser) {
    for (final StitchAuthListener listener : listeners) {
      dispatcher.dispatch(
          new Callable<Void>() {
            @Override
            public Void call() {
              listener.onUserLoggedOut(StitchAuthImpl.this, loggedOutUser);
              return null;
            }
          });
    }
  }

  @Override
  protected void onUserLinked(final StitchUser linkedUser) {
    for (final StitchAuthListener listener : listeners) {
      dispatcher.dispatch(
          new Callable<Void>() {
            @Override
            public Void call() {
              listener.onUserLinked(StitchAuthImpl.this, linkedUser);
              return null;
            }
          });
    }
  }

  @Override
  protected Document getDeviceInfo() {
    final Document info = new Document();
    if (hasDeviceId()) {
      info.put(DeviceFields.DEVICE_ID, getDeviceId());
    }
    if (appInfo.getLocalAppName() != null) {
      info.put(DeviceFields.APP_ID, appInfo.getLocalAppName());
    }
    if (appInfo.getLocalAppVersion() != null) {
      info.put(DeviceFields.APP_VERSION, appInfo.getLocalAppVersion());
    }
    info.put(DeviceFields.PLATFORM, "java-server");
    info.put(DeviceFields.PLATFORM_VERSION, System.getProperty("java.version", ""));

    final String packageVersion = Stitch.class.getPackage().getImplementationVersion();
    if (packageVersion != null && !packageVersion.isEmpty()) {
      info.put(DeviceFields.SDK_VERSION, packageVersion);
    }

    return info;
  }

  @Override
  public void refreshCustomData() {
    this.refreshAccessToken();
  }
}
