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

package com.mongodb.stitch.android.services.fcm;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.android.core.push.internal.NamedPushClientFactory;
import com.mongodb.stitch.android.core.push.internal.StitchPushClient;
import com.mongodb.stitch.android.services.fcm.internal.FcmServicePushClientImpl;
import com.mongodb.stitch.core.services.fcm.internal.CoreFcmServicePushClient;

/**
 * The FCM service push client.
 */
public interface FcmServicePushClient {

  /**
   * Registers the given FCM registration token with the currently logged in user's
   * device on Stitch.
   *
   * @param registrationToken the registration token to register.
   * @return A {@link Task} that completes when the registration is finished.
   */
  Task<Void> register(final String registrationToken);

  /**
   * Deregisters the FCM registration token bound to the currently logged in user's
   * device on Stitch.
   *
   * @return A {@link Task} that completes when the deregistration is finished.
   */
  Task<Void> deregister();

  NamedPushClientFactory<FcmServicePushClient> Factory =
      new NamedPushClientFactory<FcmServicePushClient>() {
        @Override
        public FcmServicePushClient getClient(
            final StitchPushClient pushClient,
            final TaskDispatcher dispatcher
        ) {
          return new FcmServicePushClientImpl(new CoreFcmServicePushClient(pushClient), dispatcher);
        }
      };
}
