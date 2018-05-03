package com.mongodb.stitch.android.core.internal;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.StitchAppClient;
import com.mongodb.stitch.android.core.auth.StitchAuth;
import com.mongodb.stitch.android.core.auth.internal.StitchAuthImpl;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.android.services.internal.NamedServiceClientProvider;
import com.mongodb.stitch.android.services.internal.ServiceClientProvider;
import com.mongodb.stitch.android.services.internal.StitchServiceImpl;
import com.mongodb.stitch.core.StitchAppClientConfiguration;
import com.mongodb.stitch.core.StitchAppClientInfo;
import com.mongodb.stitch.core.internal.CoreStitchAppClient;
import com.mongodb.stitch.core.internal.net.StitchAppRoutes;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import org.bson.codecs.Decoder;

public final class StitchAppClientImpl implements StitchAppClient {

  private final CoreStitchAppClient coreClient;
  private final TaskDispatcher dispatcher;
  private final StitchAppClientInfo info;
  private final StitchAppRoutes routes;
  private final StitchAuthImpl auth;

  public StitchAppClientImpl(final StitchAppClientConfiguration config) {

    this.dispatcher = new TaskDispatcher();
    this.info =
        new StitchAppClientInfo(
            config.getClientAppId(),
            config.getDataDirectory(),
            config.getLocalAppName(),
            config.getLocalAppVersion(),
            config.getCodecRegistry());
    this.routes = new StitchAppRoutes(this.info.clientAppId);
    final StitchRequestClient requestClient =
        new StitchRequestClient(
                config.getBaseURL(),
                config.getTransport(),
                config.getTransportTimeout());
    this.auth =
        new StitchAuthImpl(
            requestClient, this.routes.getAuthRoutes(), config.getStorage(), dispatcher, this.info);
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
        new StitchServiceImpl(auth, routes.getServiceRoutes(), serviceName, dispatcher), info);
  }

  @Override
  public <T> T  getServiceClient(final ServiceClientProvider<T> provider) {
    return provider.getClient(
        new StitchServiceImpl(auth, routes.getServiceRoutes(), "", dispatcher), info);
  }

  @Override
  public <TResult> Task<TResult> callFunction(final String name, final List<? extends Object> args, final Class<TResult> resultClass) {
    return dispatcher.dispatchTask(
        new Callable<TResult>() {
          @Override
          public TResult call() throws Exception {
            return coreClient.callFunctionInternal(name, args, resultClass);
          }
        });
  }

  @Override
  public <TResult> Task<TResult> callFunction(final String name, final List<? extends Object> args, final Decoder<TResult> resultDecoder) {
    return dispatcher.dispatchTask(
        new Callable<TResult>() {
          @Override
          public TResult call() throws Exception {
            return coreClient.callFunctionInternal(name, args, resultDecoder);
          }
        });
  }

  public void close() throws IOException {
    auth.close();
  }
}
