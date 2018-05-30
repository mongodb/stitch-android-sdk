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
import com.mongodb.stitch.android.services.fcm.FcmServiceClient;
import com.mongodb.stitch.core.services.fcm.FcmSendMessageRequest;
import com.mongodb.stitch.core.services.fcm.FcmSendMessageResult;
import com.mongodb.stitch.core.services.fcm.internal.CoreFcmServiceClient;
import java.util.Collection;
import java.util.concurrent.Callable;

public final class FcmServiceClientImpl implements FcmServiceClient {

  private final CoreFcmServiceClient proxy;
  private final TaskDispatcher dispatcher;

  public FcmServiceClientImpl(final CoreFcmServiceClient client, final TaskDispatcher dispatcher) {
    this.proxy = client;
    this.dispatcher = dispatcher;
  }

  /**
   * Sends an FCM message to the given target with the given request payload.
   *
   * @param to      the target to send a message to.
   * @param request the details of the message.
   * @return A {@link Task} that contains the result of sending the message.
   */
  @Override
  public Task<FcmSendMessageResult> sendMessageTo(
      final String to, final FcmSendMessageRequest request) {
    return dispatcher.dispatchTask(new Callable<FcmSendMessageResult>() {
      @Override
      public FcmSendMessageResult call() {
        return proxy.sendMessageTo(to, request);
      }
    });
  }

  /**
   * Sends an FCM message to the given set of Stitch users with the given request payload.
   *
   * @param userIds the Stitch users to send a message to.
   * @param request the details of the message.
   * @return A {@link Task} that contains the result of sending the message.
   */
  @Override
  public Task<FcmSendMessageResult> sendMessageToUsers(
      final Collection<String> userIds,
      final FcmSendMessageRequest request
  ) {
    return dispatcher.dispatchTask(new Callable<FcmSendMessageResult>() {
      @Override
      public FcmSendMessageResult call() {
        return proxy.sendMessageToUsers(userIds, request);
      }
    });
  }

  /**
   * Sends an FCM message to the given set of registration tokens with the given request payload.
   *
   * @param registrationTokens the devices to send a message to.
   * @param request            the details of the message.
   * @return A {@link Task} that contains the result of sending the message.
   */
  @Override
  public Task<FcmSendMessageResult> sendMessageToRegistrationTokens(
      final Collection<String> registrationTokens,
      final FcmSendMessageRequest request
  ) {
    return dispatcher.dispatchTask(new Callable<FcmSendMessageResult>() {
      @Override
      public FcmSendMessageResult call() {
        return proxy.sendMessageToRegistrationTokens(registrationTokens, request);
      }
    });
  }
}
