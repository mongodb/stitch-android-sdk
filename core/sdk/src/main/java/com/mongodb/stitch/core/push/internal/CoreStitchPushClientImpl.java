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


package com.mongodb.stitch.core.push.internal;

import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient;
import com.mongodb.stitch.core.internal.net.Method;
import com.mongodb.stitch.core.internal.net.StitchAuthDocRequest;
import com.mongodb.stitch.core.internal.net.StitchAuthRequest;
import org.bson.Document;

public class CoreStitchPushClientImpl implements CoreStitchPushClient {
  private final StitchAuthRequestClient requestClient;
  private final StitchPushRoutes pushRoutes;
  private final String serviceName;

  public CoreStitchPushClientImpl(
      final StitchAuthRequestClient requestClient,
      final StitchPushRoutes routes,
      final String name
  ) {
    this.requestClient = requestClient;
    this.pushRoutes = routes;
    this.serviceName = name;
  }

  public void registerInternal(final Document registrationInfo) {
    final StitchAuthDocRequest.Builder reqBuilder = new StitchAuthDocRequest.Builder();
    reqBuilder.withMethod(Method.PUT).withPath(pushRoutes.getRegistrationRoute(serviceName));
    reqBuilder.withDocument(registrationInfo);
    requestClient.doAuthenticatedRequest(reqBuilder.build());
  }

  public void deregisterInternal() {
    final StitchAuthRequest.Builder reqBuilder = new StitchAuthRequest.Builder();
    reqBuilder.withMethod(Method.DELETE).withPath(pushRoutes.getRegistrationRoute(serviceName));
    requestClient.doAuthenticatedRequest(reqBuilder.build());
  }
}
