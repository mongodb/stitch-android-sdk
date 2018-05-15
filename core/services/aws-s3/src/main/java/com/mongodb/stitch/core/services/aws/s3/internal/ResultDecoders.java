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

package com.mongodb.stitch.core.services.aws.s3.internal;

import static com.mongodb.stitch.core.internal.common.Assertions.keyPresent;

import com.mongodb.stitch.core.services.aws.s3.AwsS3PutObjectResult;
import com.mongodb.stitch.core.services.aws.s3.AwsS3SignPolicyResult;
import org.bson.BsonReader;
import org.bson.Document;
import org.bson.codecs.Decoder;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodec;

class ResultDecoders {

  static final Decoder<AwsS3PutObjectResult> putObjectResultDecoder =
      new AwsS3PutObjectResultDecoder();

  private static final class AwsS3PutObjectResultDecoder implements Decoder<AwsS3PutObjectResult> {
    @Override
    public AwsS3PutObjectResult decode(
        final BsonReader reader,
        final DecoderContext decoderContext
    ) {
      final Document document = (new DocumentCodec()).decode(reader, decoderContext);
      keyPresent(Fields.LOCATION_FIELD, document);
      return new AwsS3PutObjectResult(document.getString(Fields.LOCATION_FIELD));
    }

    private static final class Fields {
      static final String LOCATION_FIELD = "location";
    }
  }

  static final Decoder<AwsS3SignPolicyResult> signPolicyResultDecoder =
      new AwsS3SignPolicyResultDecoder();

  private static final class AwsS3SignPolicyResultDecoder
      implements Decoder<AwsS3SignPolicyResult> {
    @Override
    public AwsS3SignPolicyResult decode(
        final BsonReader reader,
        final DecoderContext decoderContext
    ) {
      final Document document = (new DocumentCodec()).decode(reader, decoderContext);
      keyPresent(Fields.POLICY_FIELD, document);
      keyPresent(Fields.SIGNATURE_FIELD, document);
      keyPresent(Fields.ALGORITHM_FIELD, document);
      keyPresent(Fields.DATE_FIELD, document);
      keyPresent(Fields.CREDENTIAL_FIELD, document);
      return new AwsS3SignPolicyResult(
          document.getString(Fields.POLICY_FIELD),
          document.getString(Fields.SIGNATURE_FIELD),
          document.getString(Fields.ALGORITHM_FIELD),
          document.getString(Fields.DATE_FIELD),
          document.getString(Fields.CREDENTIAL_FIELD));
    }

    private static final class Fields {
      static final String POLICY_FIELD = "policy";
      static final String SIGNATURE_FIELD = "signature";
      static final String ALGORITHM_FIELD = "algorithm";
      static final String DATE_FIELD = "date";
      static final String CREDENTIAL_FIELD = "credential";
    }
  }
}
