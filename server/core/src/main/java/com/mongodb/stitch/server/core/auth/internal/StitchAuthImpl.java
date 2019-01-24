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
import com.mongodb.stitch.core.internal.net.StitchRequestClient;
import com.mongodb.stitch.server.core.Stitch;
import com.mongodb.stitch.server.core.auth.StitchAuth;
import com.mongodb.stitch.server.core.auth.StitchUser;
import com.mongodb.stitch.server.core.auth.providers.internal.AuthProviderClientFactory;
import com.mongodb.stitch.server.core.auth.providers.internal.NamedAuthProviderClientFactory;

import org.bson.Document;

public final class StitchAuthImpl extends CoreStitchAuth<StitchUser> implements StitchAuth {
  private final StitchAppClientInfo appInfo;

  public StitchAuthImpl(
      final StitchRequestClient requestClient,
      final StitchAuthRoutes authRoutes,
      final Storage storage,
      final StitchAppClientInfo appInfo) {
    super(requestClient, authRoutes, storage, true);
    this.appInfo = appInfo;
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
    logoutInternal(userId);
  }

  @Override
  public void removeUser() {
    removeUserInternal();
  }

  @Override
  public void removeUserWithId(final String userId) {
    removeUserInternal(userId);
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
  protected void onAuthEvent() {}
}
