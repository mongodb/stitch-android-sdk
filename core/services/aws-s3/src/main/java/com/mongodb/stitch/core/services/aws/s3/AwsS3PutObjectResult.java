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

package com.mongodb.stitch.core.services.aws.s3;

import org.bson.BsonReader;
import org.bson.Document;
import org.bson.codecs.Decoder;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodec;

public class AwsS3PutObjectResult {
  private final String location;

  AwsS3PutObjectResult(final String location) {
    this.location = location;
  }

  public String getLocation() {
    return location;
  }

  static Decoder<AwsS3PutObjectResult> Decoder = new Decoder<AwsS3PutObjectResult>() {
    @Override
    public AwsS3PutObjectResult decode(
        final BsonReader reader,
        final DecoderContext decoderContext
    ) {
      final Document document = (new DocumentCodec()).decode(reader, decoderContext);
      if (!document.containsKey(Fields.LOCATION_FIELD)) {
        throw new IllegalStateException(
            String.format("expected %s to be present", Fields.LOCATION_FIELD));
      }
      return new AwsS3PutObjectResult(document.getString(Fields.LOCATION_FIELD));
    }
  };

  private static class Fields {
    public static final String LOCATION_FIELD = "location";
  }
}
