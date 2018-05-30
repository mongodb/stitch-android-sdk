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

package com.mongodb.stitch.server.services.twilio;

import com.mongodb.stitch.core.services.twilio.internal.CoreTwilioServiceClient;
import com.mongodb.stitch.server.core.services.internal.NamedServiceClientFactory;
import com.mongodb.stitch.server.services.twilio.internal.TwilioServiceClientImpl;
import javax.annotation.Nonnull;

/**
 * The Twilio service client.
 */
public interface TwilioServiceClient {

  /**
   * Sends an SMS/MMS message.
   *
   * @param to the number to send the message to.
   * @param from the number that the message is from.
   * @param body the body text of the message.
   */
  void sendMessage(
      @Nonnull final String to,
      @Nonnull final String from,
      @Nonnull final String body);

  /**
   * Sends an SMS/MMS message.
   *
   * @param to the number to send the message to.
   * @param from the number that the message is from.
   * @param body the body text of the message.
   * @param mediaUrl the URL of the media to send in an MMS.
   */
  void sendMessage(
      @Nonnull final String to,
      @Nonnull final String from,
      @Nonnull final String body,
      @Nonnull final String mediaUrl);

  NamedServiceClientFactory<TwilioServiceClient> Factory =
      (service, appInfo) -> new TwilioServiceClientImpl(new CoreTwilioServiceClient(service));
}
