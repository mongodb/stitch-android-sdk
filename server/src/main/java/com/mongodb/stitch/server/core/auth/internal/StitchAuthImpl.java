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
import com.mongodb.stitch.server.core.auth.providers.AuthProviderClientSupplier;
import com.mongodb.stitch.server.core.auth.providers.NamedAuthProviderClientSupplier;
import org.bson.Document;

public final class StitchAuthImpl extends CoreStitchAuth<StitchUser> implements StitchAuth {
  private final StitchAppClientInfo appInfo;

  public StitchAuthImpl(
      final StitchRequestClient requestClient,
      final StitchAuthRoutes authRoutes,
      final Storage storage,
      final StitchAppClientInfo appInfo) {
    super(requestClient, authRoutes, storage, appInfo.configuredCodecRegistry);
    this.appInfo = appInfo;
  }

  protected StitchUserFactory<StitchUser> getUserFactory() {
    return new StitchUserFactoryImpl(this);
  }

  @Override
  public <T> T getProviderClient(final AuthProviderClientSupplier<T> provider) {
    return provider.getClient(requestClient, authRoutes);
  }

  @Override
  public <T> T getProviderClient(
      final NamedAuthProviderClientSupplier<T> provider, final String providerName) {
    return provider.getClient(providerName, requestClient, authRoutes);
  }

  @Override
  public StitchUser loginWithCredential(final StitchCredential credential) {
    return loginWithCredentialBlocking(credential);
  }

  StitchUser linkWithCredential(final CoreStitchUser user, final StitchCredential credential) {
    return linkUserWithCredentialBlocking(user, credential);
  }

  @Override
  public void logout() {
    logoutBlocking();
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
    info.put(DeviceFields.PLATFORM, System.getProperty("java.version", ""));
    info.put(DeviceFields.PLATFORM_VERSION, "java-server");

    final String packageVersion = Stitch.class.getPackage().getImplementationVersion();
    if (packageVersion != null && !packageVersion.isEmpty()) {
      info.put(DeviceFields.SDK_VERSION, packageVersion);
    }

    return info;
  }

  @Override
  protected void onAuthEvent() {
  }
}
