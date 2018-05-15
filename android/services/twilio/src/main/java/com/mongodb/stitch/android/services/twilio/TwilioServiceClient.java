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

package com.mongodb.stitch.android.services.twilio;

import android.support.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.android.core.services.StitchService;
import com.mongodb.stitch.android.core.services.internal.NamedServiceClientFactory;
import com.mongodb.stitch.android.services.twilio.internal.TwilioServiceClientImpl;
import com.mongodb.stitch.core.StitchAppClientInfo;

public interface TwilioServiceClient {

  /**
   * Sends an SMS/MMS message.
   *
   * @param to the number to send the message to.
   * @param from the number that the message is from.
   * @param body the body text of the message.
   * @return a task that completes when the send is done.
   */
  Task<Void> sendMessage(
      @NonNull final String to,
      @NonNull final String from,
      @NonNull final String body);

  /**
   * Sends an SMS/MMS message.
   *
   * @param to the number to send the message to.
   * @param from the number that the message is from.
   * @param body the body text of the message.
   * @param mediaUrl the URL of the media to send in an MMS.
   * @return a task that completes when the send is done.
   */
  Task<Void> sendMessage(
      @NonNull final String to,
      @NonNull final String from,
      @NonNull final String body,
      @NonNull final String mediaUrl);

  NamedServiceClientFactory<TwilioServiceClient> Factory =
      new NamedServiceClientFactory<TwilioServiceClient>() {
        @Override
        public TwilioServiceClientImpl getClient(
            final StitchService service,
            final StitchAppClientInfo appInfo,
            final TaskDispatcher dispatcher
        ) {
          return new TwilioServiceClientImpl(service, dispatcher);
        }
      };
}
