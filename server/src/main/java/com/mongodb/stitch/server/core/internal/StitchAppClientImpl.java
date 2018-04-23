package com.mongodb.stitch.server.core.internal;

import com.mongodb.stitch.core.StitchAppClientConfiguration;
import com.mongodb.stitch.core.StitchAppClientInfo;
import com.mongodb.stitch.core.internal.CoreStitchAppClient;
import com.mongodb.stitch.core.internal.net.StitchAppRoutes;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;
import com.mongodb.stitch.server.core.StitchAppClient;
import com.mongodb.stitch.server.core.auth.StitchAuth;
import com.mongodb.stitch.server.core.auth.internal.StitchAuthImpl;
import com.mongodb.stitch.server.core.services.internal.NamedServiceClientProvider;
import com.mongodb.stitch.server.core.services.internal.ServiceClientProvider;
import com.mongodb.stitch.server.core.services.internal.StitchServiceImpl;

import org.bson.codecs.Decoder;

import java.util.List;

public final class StitchAppClientImpl implements StitchAppClient {

  private final CoreStitchAppClient coreClient;
  private final StitchAppClientInfo info;
  private final StitchAppRoutes routes;
  private final StitchAuthImpl auth;

  public StitchAppClientImpl(final StitchAppClientConfiguration config) {

    this.info =
        new StitchAppClientInfo(
            config.getClientAppId(),
            config.getDataDirectory(),
            config.getLocalAppName(),
            config.getLocalAppVersion(),
            config.getCodecRegistry());
    this.routes = new StitchAppRoutes(this.info.clientAppId);
    final StitchRequestClient requestClient =
        new StitchRequestClient(config.getBaseURL(), config.getTransport());
    this.auth =
        new StitchAuthImpl(
            requestClient, this.routes.getAuthRoutes(), config.getStorage(), this.info);
    this.coreClient = new CoreStitchAppClient(this.auth, this.routes);
  }

  @Override
  public StitchAuth getAuth() {
    return auth;
  }

  @Override
  public <T> T getServiceClient(
      final NamedServiceClientProvider<T> provider, final String serviceName) {
    return provider.getClient(
        new StitchServiceImpl(auth, routes.getServiceRoutes(), serviceName), info);
  }

  @Override
  public <T> T getServiceClient(final ServiceClientProvider<T> provider) {
    return provider.getClient(new StitchServiceImpl(auth, routes.getServiceRoutes(), ""), info);
  }

  @Override
  public <TResult> TResult callFunction(final String name, final List<? extends Object> args, final Class<TResult> resultClass) {
    return coreClient.callFunctionInternal(name, args, resultClass);
  }

  @Override
  public <TResult> TResult callFunction(final String name, final List<? extends Object> args, final Decoder<TResult> resultDecoder) {
    return coreClient.callFunctionInternal(name, args, resultDecoder);
  }
}
