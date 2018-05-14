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

package com.mongodb.stitch.core.services.twilio;

import com.mongodb.stitch.core.services.internal.CoreStitchService;
import java.util.Collections;
import org.bson.Document;

public class CoreTwilioServiceClient {

  private final CoreStitchService service;

  protected CoreTwilioServiceClient(final CoreStitchService service) {
    this.service = service;
  }

  protected void sendMessageInternal(
      final String to,
      final String from,
      final String body,
      final String mediaUrl
  ) {
    final Document args = new Document();
    args.put("to", to);
    args.put("from", from);
    args.put("body", body);
    if (mediaUrl != null) {
      args.put("mediaUrl", mediaUrl);
    }
    service.callFunctionInternal("send", Collections.singletonList(args));
  }

  protected void sendMessageInternal(final String to, final String from, final String body) {
    sendMessageInternal(to, from, body, null);
  }
}
