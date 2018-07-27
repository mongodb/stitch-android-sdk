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

package com.mongodb.stitch.core.services.aws.ses.internal;

import com.mongodb.stitch.core.services.aws.ses.AwsSesSendResult;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;

import java.util.Collections;
import org.bson.Document;

/**
 * @deprecated use AwsServiceClient instead.
 */
@Deprecated
public class CoreAwsSesServiceClient {

  private final CoreStitchServiceClient service;

  public CoreAwsSesServiceClient(final CoreStitchServiceClient service) {
    this.service = service;
  }

  public AwsSesSendResult sendEmail(
      final String toAddress,
      final String fromAddress,
      final String subject,
      final String body
  ) {
    final Document args = new Document();
    args.put("toAddress", toAddress);
    args.put("fromAddress", fromAddress);
    args.put("subject", subject);
    args.put("body", body);
    return service.callFunction(
        "send",
        Collections.singletonList(args),
        ResultDecoders.sendResultDecoder);
  }
}
