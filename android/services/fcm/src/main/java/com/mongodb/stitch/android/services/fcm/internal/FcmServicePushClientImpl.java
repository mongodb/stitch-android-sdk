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

package com.mongodb.stitch.android.services.fcm.internal;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.android.services.fcm.FcmServicePushClient;
import com.mongodb.stitch.core.services.fcm.internal.CoreFcmServicePushClient;
import java.util.concurrent.Callable;

public final class FcmServicePushClientImpl implements FcmServicePushClient {

  private final CoreFcmServicePushClient proxy;
  private final TaskDispatcher dispatcher;

  public FcmServicePushClientImpl(
      final CoreFcmServicePushClient client,
      final TaskDispatcher dispatcher
  ) {
    this.proxy = client;
    this.dispatcher = dispatcher;
  }

  public Task<Void> register(final String registrationToken) {
    return dispatcher.dispatchTask(new Callable<Void>() {
      @Override
      public Void call() {
        proxy.register(registrationToken);
        return null;
      }
    });
  }

  public Task<Void> deregister() {
    return dispatcher.dispatchTask(new Callable<Void>() {
      @Override
      public Void call() {
        proxy.deregister();
        return null;
      }
    });
  }
}
