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

package com.mongodb.stitch.android.services.twilio.internal;

import android.support.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.android.core.services.StitchService;
import com.mongodb.stitch.android.services.twilio.TwilioServiceClient;
import com.mongodb.stitch.core.services.twilio.CoreTwilioServiceClient;

import java.util.concurrent.Callable;

public final class TwilioServiceClientImpl extends CoreTwilioServiceClient
    implements TwilioServiceClient {

  private final TaskDispatcher dispatcher;

  public TwilioServiceClientImpl(final StitchService service, final TaskDispatcher dispatcher) {
    super(service);
    this.dispatcher = dispatcher;
  }

  /**
   * Sends an SMS/MMS message.
   *
   * @param to The number to send the message to.
   * @param from The number that the message is from.
   * @param body The body text of the message.
   * @return A task that completes when the send is done.
   */
  public Task<Void> sendMessage(
      @NonNull final String to,
      @NonNull final String from,
      @NonNull final String body) {
    return dispatcher.dispatchTask(new Callable<Void>() {
      @Override
      public Void call() {
        sendMessageInternal(to, from, body);
        return null;
      }
    });
  }

  /**
   * Sends an SMS/MMS message.
   *
   * @param to The number to send the message to.
   * @param from The number that the message is from.
   * @param body The body text of the message.
   * @param mediaUrl The URL of the media to send in an MMS.
   * @return A task that completes when the send is done.
   */
  public Task<Void> sendMessage(
      @NonNull final String to,
      @NonNull final String from,
      @NonNull final String body,
      @NonNull final String mediaUrl) {
    return dispatcher.dispatchTask(new Callable<Void>() {
      @Override
      public Void call() {
        sendMessageInternal(to, from, body, mediaUrl);
        return null;
      }
    });
  }
}
