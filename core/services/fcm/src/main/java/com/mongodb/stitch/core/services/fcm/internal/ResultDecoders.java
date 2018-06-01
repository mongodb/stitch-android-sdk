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

package com.mongodb.stitch.core.services.fcm.internal;

import static com.mongodb.stitch.core.internal.common.Assertions.keyPresent;

import com.mongodb.stitch.core.services.fcm.FcmSendMessageResult;
import com.mongodb.stitch.core.services.fcm.FcmSendMessageResultFailureDetail;
import java.util.ArrayList;
import java.util.List;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonReader;
import org.bson.BsonValue;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.Decoder;
import org.bson.codecs.DecoderContext;

class ResultDecoders {

  static final Decoder<FcmSendMessageResult> sendMessageResponseDecoder =
      new SendMessageResponseDecoder();

  private static final class SendMessageResponseDecoder implements Decoder<FcmSendMessageResult> {
    @Override
    public FcmSendMessageResult decode(
        final BsonReader reader,
        final DecoderContext decoderContext
    ) {
      final BsonDocument document = (new BsonDocumentCodec()).decode(reader, decoderContext);
      keyPresent(Fields.SUCCESSES_FIELD, document);
      keyPresent(Fields.FAILURES_FIELD, document);

      final List<FcmSendMessageResultFailureDetail> failureDetails;
      if (document.containsKey(Fields.FAILURE_DETAILS_FIELD)) {
        final BsonArray detailsArr = document.getArray(Fields.FAILURE_DETAILS_FIELD);
        failureDetails = new ArrayList<>(detailsArr.size());
        for (final BsonValue detail: detailsArr) {
          final BsonDocument detailDoc = detail.asDocument();
          final String userId;
          if (detailDoc.containsKey(Fields.FAILURE_DETAIL_USER_ID_FIELD)) {
            userId = detailDoc.getString(Fields.FAILURE_DETAIL_USER_ID_FIELD).getValue();
          } else {
            userId = null;
          }
          failureDetails.add(new FcmSendMessageResultFailureDetail(
              detailDoc.getNumber(Fields.FAILURE_DETAIL_INDEX_FIELD).longValue(),
              detailDoc.getString(Fields.FAILURE_DETAIL_ERROR_FIELD).getValue(),
              userId));
        }
      } else {
        failureDetails = null;
      }

      return new FcmSendMessageResult(
          document.getNumber(Fields.SUCCESSES_FIELD).longValue(),
          document.getNumber(Fields.FAILURES_FIELD).longValue(),
          failureDetails);
    }

    private static final class Fields {
      static final String SUCCESSES_FIELD = "successes";
      static final String FAILURES_FIELD = "failures";
      static final String FAILURE_DETAILS_FIELD = "failureDetails";
      static final String FAILURE_DETAIL_INDEX_FIELD = "index";
      static final String FAILURE_DETAIL_ERROR_FIELD = "error";
      static final String FAILURE_DETAIL_USER_ID_FIELD = "userId";
    }
  }
}
