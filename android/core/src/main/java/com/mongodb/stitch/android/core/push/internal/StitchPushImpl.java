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


package com.mongodb.stitch.android.core.push.internal;

import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.android.core.push.StitchPush;
import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient;
import com.mongodb.stitch.core.push.internal.StitchPushRoutes;

public class StitchPushImpl implements StitchPush {
  private final StitchAuthRequestClient requestClient;
  private final StitchPushRoutes pushRoutes;
  private final TaskDispatcher dispatcher;

  public StitchPushImpl(
      final StitchAuthRequestClient requestClient,
      final StitchPushRoutes pushRoutes,
      final TaskDispatcher dispatcher
  ) {
    this.requestClient = requestClient;
    this.pushRoutes = pushRoutes;
    this.dispatcher = dispatcher;
  }

  @Override
  public <T> T getClient(final NamedPushClientFactory<T> factory, final String serviceName) {
    return factory.getClient(
        new StitchPushClientImpl(
            requestClient,
            pushRoutes,
            serviceName,
            dispatcher),
        dispatcher);
  }
}
