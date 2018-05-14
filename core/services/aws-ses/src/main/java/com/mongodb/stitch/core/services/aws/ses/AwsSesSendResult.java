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

package com.mongodb.stitch.core.services.aws.ses;

import org.bson.BsonReader;
import org.bson.Document;
import org.bson.codecs.Decoder;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodec;

public class AwsSesSendResult {
  private final String messageId;

  AwsSesSendResult(final String messageId) {
    this.messageId = messageId;
  }

  public String getMessageId() {
    return messageId;
  }

  static Decoder<AwsSesSendResult> Decoder = new Decoder<AwsSesSendResult>() {
    @Override
    public AwsSesSendResult decode(final BsonReader reader, final DecoderContext decoderContext) {
      final Document document = (new DocumentCodec()).decode(reader, decoderContext);
      if (!document.containsKey(Fields.MESSAGE_ID_FIELD)) {
        throw new IllegalStateException(
            String.format("expected %s to be present", Fields.MESSAGE_ID_FIELD));
      }
      return new AwsSesSendResult(document.getString(Fields.MESSAGE_ID_FIELD));
    }
  };

  private static class Fields {
    public static final String MESSAGE_ID_FIELD = "messageId";
  }
}
