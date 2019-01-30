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

package com.mongodb.stitch.server.core.internal;

import com.mongodb.stitch.core.StitchAppClientConfiguration;
import com.mongodb.stitch.core.StitchAppClientInfo;
import com.mongodb.stitch.core.internal.CoreStitchAppClient;
import com.mongodb.stitch.core.internal.common.AuthMonitor;
import com.mongodb.stitch.core.internal.common.ThreadDispatcher;
import com.mongodb.stitch.core.internal.net.StitchAppRoutes;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;
import com.mongodb.stitch.core.internal.net.StitchRequestClientImpl;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClientImpl;
import com.mongodb.stitch.server.core.StitchAppClient;
import com.mongodb.stitch.server.core.auth.StitchAuth;
import com.mongodb.stitch.server.core.auth.internal.StitchAuthImpl;
import com.mongodb.stitch.server.core.services.StitchServiceClient;
import com.mongodb.stitch.server.core.services.internal.NamedServiceClientFactory;
import com.mongodb.stitch.server.core.services.internal.ServiceClientFactory;
import com.mongodb.stitch.server.core.services.internal.StitchServiceClientImpl;
import java.io.IOException;
import java.util.List;
import org.bson.codecs.Decoder;
import org.bson.codecs.configuration.CodecRegistry;

import javax.annotation.Nullable;

public final class StitchAppClientImpl implements StitchAppClient, AuthMonitor {

  private final CoreStitchAppClient coreClient;
  private final StitchAppClientInfo info;
  private final StitchAppRoutes routes;
  private final StitchAuthImpl auth;

  /**
   * Constructs an app client with the given configuration.
   *
   * @param clientAppId the client app id for the app.
   * @param config the configuration to use for the app client.
   */
  public StitchAppClientImpl(
      final String clientAppId,
      final StitchAppClientConfiguration config
  ) {
    this.info =
        new StitchAppClientInfo(
            clientAppId,
            config.getDataDirectory(),
            config.getLocalAppName(),
            config.getLocalAppVersion(),
            config.getCodecRegistry(),
            config.getNetworkMonitor(),
            this,
            new ThreadDispatcher());
    this.routes = new StitchAppRoutes(this.info.getClientAppId());
    final StitchRequestClient requestClient =
        new StitchRequestClientImpl(
                config.getBaseUrl(), config.getTransport(), config.getDefaultRequestTimeout());
    this.auth =
        new StitchAuthImpl(
            requestClient, this.routes.getAuthRoutes(), config.getStorage(), this.info);
    this.coreClient = new CoreStitchAppClient(this.auth, this.routes, this.info.getCodecRegistry());
  }

  @Override
  public StitchAuth getAuth() {
    return auth;
  }

  @Override
  public <T> T getServiceClient(
      final NamedServiceClientFactory<T> factory, final String serviceName) {
    return factory.getClient(
        new CoreStitchServiceClientImpl(
            auth,
            routes.getServiceRoutes(),
            serviceName,
            info.getCodecRegistry()),
        info);
  }

  @Override
  public <T> T getServiceClient(final ServiceClientFactory<T> factory) {
    return factory.getClient(
        new CoreStitchServiceClientImpl(
            auth,
            routes.getServiceRoutes(),
            "",
            info.getCodecRegistry()),
        info);
  }

  @Override
  public StitchServiceClient getServiceClient(final String serviceName) {
    return new StitchServiceClientImpl(
        new CoreStitchServiceClientImpl(
            auth,
            routes.getServiceRoutes(),
            serviceName,
            info.getCodecRegistry()));
  }

  @Override
  public void callFunction(
      final String name, final List<?> args) {
    coreClient.callFunction(name, args, null);
  }

  @Override
  public void callFunction(
      final String name,
      final List<?> args,
      final Long requestTimeout) {
    coreClient.callFunction(name, args, requestTimeout);
  }

  @Override
  public <ResultT> ResultT callFunction(
      final String name, final List<?> args, final Class<ResultT> resultClass) {
    return coreClient.callFunction(name, args, null, resultClass);
  }

  @Override
  public <ResultT> ResultT callFunction(
      final String name,
      final List<?> args,
      final Long requestTimeout,
      final Class<ResultT> resultClass) {
    return coreClient.callFunction(name, args, requestTimeout, resultClass);
  }

  @Override
  public <ResultT> ResultT callFunction(
      final String name,
      final List<?> args,
      final Class<ResultT> resultClass,
      final CodecRegistry codecRegistry
  ) {
    return coreClient.callFunction(name, args, null, resultClass, codecRegistry);
  }

  @Override
  public <ResultT> ResultT callFunction(
      final String name,
      final List<?> args,
      final Long requestTimeout,
      final Class<ResultT> resultClass,
      final CodecRegistry codecRegistry
  ) {
    return coreClient.callFunction(name, args, requestTimeout, resultClass, codecRegistry);
  }

  @Override
  public <ResultT> ResultT callFunction(
      final String name, final List<?> args, final Decoder<ResultT> resultDecoder) {
    return coreClient.callFunction(name, args, null, resultDecoder);
  }

  @Override
  public <ResultT> ResultT callFunction(
      final String name,
      final List<?> args,
      final Long requestTimeout,
      final Decoder<ResultT> resultDecoder) {
    return coreClient.callFunction(name, args, requestTimeout, resultDecoder);
  }

  @Override
  public boolean isLoggedIn() {
    return getAuth().isLoggedIn();
  }

  @Nullable
  @Override
  public String getActiveUserId() {
    return getAuth().getUser() != null ? getAuth().getUser().getId() : null;
  }

  /**
   * Closes the client and shuts down all background operations.
   */
  @Override
  public void close() throws IOException {
    auth.close();
  }
}
