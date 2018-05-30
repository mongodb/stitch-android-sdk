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

package com.mongodb.stitch.server.services.fcm;

import com.mongodb.stitch.core.services.fcm.FcmSendMessageRequest;
import com.mongodb.stitch.core.services.fcm.FcmSendMessageResult;
import com.mongodb.stitch.core.services.fcm.internal.CoreFcmServiceClient;
import com.mongodb.stitch.server.core.services.internal.NamedServiceClientFactory;
import com.mongodb.stitch.server.services.fcm.internal.FcmServiceClientImpl;
import java.util.Collection;

/**
 * The FCM service client.
 */
public interface FcmServiceClient {

  /**
   * Sends an FCM message to the given target with the given request payload.
   *
   * @param to the target to send a message to.
   * @param request the details of the message.
   * @return the result of sending the message.
   */
  FcmSendMessageResult sendMessageTo(
      final String to,
      final FcmSendMessageRequest request);

  /**
   * Sends an FCM message to the given set of Stitch users with the given request payload.
   *
   * @param userIds the Stitch users to send a message to.
   * @param request the details of the message.
   * @return the result of sending the message.
   */
  FcmSendMessageResult sendMessageToUsers(
      final Collection<String> userIds,
      final FcmSendMessageRequest request);

  /**
   * Sends an FCM message to the given set of registration tokens with the given request payload.
   *
   * @param registrationTokens the devices to send a message to.
   * @param request the details of the message.
   * @return the result of sending the message.
   */
  FcmSendMessageResult sendMessageToRegistrationTokens(
      final Collection<String> registrationTokens,
      final FcmSendMessageRequest request);

  NamedServiceClientFactory<FcmServiceClient> Factory =
      (service, appInfo) -> new FcmServiceClientImpl(new CoreFcmServiceClient(service));
}
