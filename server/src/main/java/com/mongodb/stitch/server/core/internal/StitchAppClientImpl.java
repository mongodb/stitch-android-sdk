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
import com.mongodb.stitch.core.internal.net.StitchAppRoutes;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;
import com.mongodb.stitch.server.core.StitchAppClient;
import com.mongodb.stitch.server.core.auth.StitchAuth;
import com.mongodb.stitch.server.core.auth.internal.StitchAuthImpl;
import com.mongodb.stitch.server.core.services.internal.NamedServiceClientProvider;
import com.mongodb.stitch.server.core.services.internal.ServiceClientProvider;
import com.mongodb.stitch.server.core.services.internal.StitchServiceImpl;
import java.util.List;
import org.bson.codecs.Decoder;

public final class StitchAppClientImpl implements StitchAppClient {

  private final CoreStitchAppClient coreClient;
  private final StitchAppClientInfo info;
  private final StitchAppRoutes routes;
  private final StitchAuthImpl auth;

  /**
   * Constructs an app client with the given configuration.
   *
   * @param config The configuration to use for the app client.
   */
  public StitchAppClientImpl(final StitchAppClientConfiguration config) {

    this.info =
        new StitchAppClientInfo(
            config.getClientAppId(),
            config.getDataDirectory(),
            config.getLocalAppName(),
            config.getLocalAppVersion(),
            config.getCodecRegistry());
    this.routes = new StitchAppRoutes(this.info.getClientAppId());
    final StitchRequestClient requestClient =
        new StitchRequestClient(
                config.getBaseUrl(), config.getTransport(), config.getDefaultRequestTimeout());
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
  public <ResultT> ResultT callFunction(
      final String name, final List<? extends Object> args, final Class<ResultT> resultClass) {
    return coreClient.callFunctionInternal(name, args, null, resultClass);
  }

  @Override
  public <ResultT> ResultT callFunction(
          final String name,
          final List<? extends Object> args,
          final Long requestTimeout,
          final Class<ResultT> resultClass) {
    return coreClient.callFunctionInternal(name, args, requestTimeout, resultClass);
  }

  @Override
  public <ResultT> ResultT callFunction(
      final String name, final List<? extends Object> args, final Decoder<ResultT> resultDecoder) {
    return coreClient.callFunctionInternal(name, args, null, resultDecoder);
  }

  @Override
  public <ResultT> ResultT callFunction(
          final String name,
          final List<? extends Object> args,
          final Long requestTimeout,
          final Decoder<ResultT> resultDecoder) {
    return coreClient.callFunctionInternal(name, args, requestTimeout, resultDecoder);
  }
}
