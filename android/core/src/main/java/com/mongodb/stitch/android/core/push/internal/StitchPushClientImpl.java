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

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient;
import com.mongodb.stitch.core.push.internal.CoreStitchPushClientImpl;
import com.mongodb.stitch.core.push.internal.StitchPushRoutes;

import java.util.concurrent.Callable;
import org.bson.Document;

public class StitchPushClientImpl extends CoreStitchPushClientImpl implements StitchPushClient {
  private final TaskDispatcher dispatcher;

  StitchPushClientImpl(
      final StitchAuthRequestClient requestClient,
      final StitchPushRoutes routes,
      final String name,
      final TaskDispatcher dispatcher
  ) {
    super(requestClient, routes, name);
    this.dispatcher = dispatcher;
  }


  @Override
  public Task<Void> register(final Document registrationInfo) {
    return dispatcher.dispatchTask(new Callable<Void>() {
      @Override
      public Void call() {
        registerInternal(registrationInfo);
        return null;
      }
    });
  }

  @Override
  public Task<Void> deregister() {
    return dispatcher.dispatchTask(new Callable<Void>() {
      @Override
      public Void call() {
        deregisterInternal();
        return null;
      }
    });
  }
}
