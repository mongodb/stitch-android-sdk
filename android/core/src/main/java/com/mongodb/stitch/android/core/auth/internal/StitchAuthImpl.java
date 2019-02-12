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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import org.bson.Document;

import javax.annotation.Nullable;

/**
 * The Android specific authentication component for clients that acts as a {@link StitchAuth} and
 * an {@link com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient}.
 */
public final class StitchAuthImpl extends CoreStitchAuth<StitchUser> implements StitchAuth {
  private final TaskDispatcher dispatcher;
  private final StitchAppClientInfo appInfo;
  /**
   * A set of auth listeners that should be dispatched to asynchronously
   */
  private final Set<StitchAuthListener> listeners = new HashSet<>();
  /**
   * A set of auth listeners that should be dispatched to synchronously.
   * For internal use.
   */
  private final Set<StitchAuthListener> synchronousListeners = new HashSet<>();

  /**
   * Constructs a {@link StitchAuthImpl}.
   *
   * @param requestClient the request client to use for any Stitch requests.
   * @param authRoutes auth specific routes.
   * @param storage where to store/retrieve authentication data.
   * @param dispatcher where to send asynchronous requests to.
   * @param appInfo information about the application.
   */
  public StitchAuthImpl(
      final StitchRequestClient requestClient,
      final StitchAuthRoutes authRoutes,
      final Storage storage,
      final TaskDispatcher dispatcher,
      final StitchAppClientInfo appInfo) {
    super(requestClient, authRoutes, storage, true);
    this.dispatcher = dispatcher;
    this.appInfo = appInfo;
  }

  protected StitchUserFactory<StitchUser> getUserFactory() {
    return new StitchUserFactoryImpl(this);
  }

  @Override
  public <ClientT> ClientT getProviderClient(
          final AuthProviderClientFactory<ClientT> factory) {
    return factory.getClient(this, getRequestClient(), getAuthRoutes(), dispatcher);
  }

  @Override
  public <T> T getProviderClient(
      final NamedAuthProviderClientFactory<T> factory, final String providerName) {
    return factory.getClient(providerName, getRequestClient(), getAuthRoutes(), dispatcher);
  }

  @Override
  public Task<StitchUser> loginWithCredential(final StitchCredential credential) {
    return dispatcher.dispatchTask(
        new Callable<StitchUser>() {
          @Override
          public StitchUser call() {
            return loginWithCredentialInternal(credential);
          }
        });
  }

  Task<StitchUser> linkWithCredential(
      final CoreStitchUser user, final StitchCredential credential) {
    return dispatcher.dispatchTask(
        new Callable<StitchUser>() {
          @Override
          public StitchUser call() {
            return linkUserWithCredentialInternal(user, credential);
          }
        });
  }

  @Override
  public Task<Void> logout() {
    return dispatcher.dispatchTask(
        new Callable<Void>() {
          @Override
          public Void call() {
            logoutInternal();
            return null;
          }
        });
  }

  @Override
  public Task<Void> logoutUserWithId(final String userId) {
    return dispatcher.dispatchTask(
        new Callable<Void>() {
          @Override
          public Void call() {
            logoutUserWithIdInternal(userId);
            return null;
          }
        });
  }

  @Override
  public Task<Void> removeUser() {
    return dispatcher.dispatchTask(
        new Callable<Void>() {
          @Override
          public Void call() {
            removeUserInternal();
            return null;
          }
        });
  }

  @Override
  public Task<Void> removeUserWithId(final String userId) {
    return dispatcher.dispatchTask(
        new Callable<Void>() {
          @Override
          public Void call() {
            removeUserWithIdInternal(userId);
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
    dispatcher.dispatchTask(
        new Callable<Void>() {
          @Override
          public Void call() {
            listener.onListenerRegistered(StitchAuthImpl.this);
            return null;
          }
        });
  }

  public void addSynchronousAuthListener(final StitchAuthListener listener) {
    synchronized (this) {
      synchronousListeners.add(listener);
    }

    // Trigger the onUserLoggedIn event in case some event happens and
    // this caller would miss out on this event other wise.
    onAuthEvent(listener);
    listener.onListenerRegistered(this);
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
          public Void call() {
            listener.onAuthEvent(auth);
            return null;
          }
        });
  }

  @Override
  protected void onAuthEvent() {
    for (final StitchAuthListener listener : listeners) {
      dispatcher.dispatchTask(
          new Callable<Void>() {
            @Override
            public Void call() {
              listener.onAuthEvent(StitchAuthImpl.this);
              return null;
            }
          });
    }
    for (final StitchAuthListener listener : synchronousListeners) {
      listener.onAuthEvent(this);
    }
  }

  @Override
  protected void onListenerInitialized() {
    for (final StitchAuthListener listener : listeners) {
      dispatcher.dispatchTask(
          new Callable<Void>() {
            @Override
            public Void call() {
              listener.onListenerRegistered(StitchAuthImpl.this);
              return null;
            }
          });
    }
    for (final StitchAuthListener listener : synchronousListeners) {
      listener.onListenerRegistered(this);
    }
  }

  @Override
  protected void onActiveUserChanged(@Nullable StitchUser currentActiveUser,
                                     @Nullable StitchUser previousActiveUser) {
    for (final StitchAuthListener listener : listeners) {
      dispatcher.dispatchTask(
          new Callable<Void>() {
            @Override
            public Void call() {
              listener.onActiveUserChanged(
                  StitchAuthImpl.this, currentActiveUser, previousActiveUser);
              return null;
            }
          });
    }
    for (final StitchAuthListener listener : synchronousListeners) {
      listener.onActiveUserChanged(
          this, currentActiveUser, previousActiveUser);
    }
  }

  @Override
  protected void onUserAdded(StitchUser createdUser) {
    for (final StitchAuthListener listener : listeners) {
      dispatcher.dispatchTask(
          new Callable<Void>() {
            @Override
            public Void call() {
              listener.onUserAdded(
                  StitchAuthImpl.this, createdUser);
              return null;
            }
          });
    }
    for (final StitchAuthListener listener : synchronousListeners) {
      listener.onUserAdded(this, createdUser);
    }
  }

  @Override
  protected void onUserLoggedIn(StitchUser loggedInUser) {
    for (final StitchAuthListener listener : listeners) {
      dispatcher.dispatchTask(
          new Callable<Void>() {
            @Override
            public Void call() {
              listener.onUserLoggedIn(
                  StitchAuthImpl.this, loggedInUser);
              return null;
            }
          });
    }
    for (final StitchAuthListener listener : synchronousListeners) {
      listener.onUserLoggedIn(this, loggedInUser);
    }
  }

  @Override
  protected void onUserRemoved(StitchUser removedUser) {
    for (final StitchAuthListener listener : listeners) {
      dispatcher.dispatchTask(
          new Callable<Void>() {
            @Override
            public Void call() {
              listener.onUserRemoved(StitchAuthImpl.this, removedUser);
              return null;
            }
          });
    }
    for (final StitchAuthListener listener : synchronousListeners) {
      listener.onUserRemoved(this, removedUser);
    }
  }

  @Override
  protected void onUserLoggedOut(StitchUser loggedOutUser) {
    for (final StitchAuthListener listener : listeners) {
      dispatcher.dispatchTask(
          new Callable<Void>() {
            @Override
            public Void call() {
              listener.onUserLoggedOut(StitchAuthImpl.this, loggedOutUser);
              return null;
            }
          });
    }
    for (final StitchAuthListener listener : synchronousListeners) {
      listener.onUserLoggedOut(this, loggedOutUser);
    }
  }

  @Override
  protected void onUserLinked(StitchUser linkedUser) {
    for (final StitchAuthListener listener : listeners) {
      dispatcher.dispatchTask(
          new Callable<Void>() {
            @Override
            public Void call() {
              listener.onUserLinked(StitchAuthImpl.this, linkedUser);
              return null;
            }
          });
    }
    for (final StitchAuthListener listener : synchronousListeners) {
      listener.onUserLinked(this, linkedUser);
    }
  }
}
