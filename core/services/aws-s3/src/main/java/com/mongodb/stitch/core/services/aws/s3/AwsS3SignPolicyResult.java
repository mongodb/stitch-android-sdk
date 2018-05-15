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

public class AwsS3SignPolicyResult {
  private final String policy;
  private final String signature;
  private final String algorithm;
  private final String date;
  private final String credential;

  AwsS3SignPolicyResult(
      final String policy,
      final String signature,
      final String algorithm,
      final String date,
      final String credential
  ) {
    this.policy = policy;
    this.signature = signature;
    this.algorithm = algorithm;
    this.date = date;
    this.credential = credential;
  }

  /**
   * Returns the description of the policy that has been signed.
   */
  public String getPolicy() {
    return policy;
  }

  /**
   * Returns the computed signature of the policy.
   */
  public String getSignature() {
    return signature;
  }

  /**
   * Returns the algorithm used to compute the signature.
   */
  public String getAlgorithm() {
    return algorithm;
  }

  /**
   * Returns the date at which the signature was computed.
   */
  public String getDate() {
    return date;
  }

  /**
   * Returns the credential that should be used when utilizing this signed policy.
   */
  public String getCredential() {
    return credential;
  }

  static Decoder<AwsS3SignPolicyResult> Decoder = new Decoder<AwsS3SignPolicyResult>() {
    @Override
    public AwsS3SignPolicyResult decode(
        final BsonReader reader,
        final DecoderContext decoderContext
    ) {
      final Document document = (new DocumentCodec()).decode(reader, decoderContext);
      if (!document.containsKey(Fields.POLICY_FIELD)) {
        throw new IllegalStateException(
            String.format("expected %s to be present", Fields.POLICY_FIELD));
      }
      if (!document.containsKey(Fields.SIGNATURE_FIELD)) {
        throw new IllegalStateException(
            String.format("expected %s to be present", Fields.SIGNATURE_FIELD));
      }
      if (!document.containsKey(Fields.ALGORITHM_FIELD)) {
        throw new IllegalStateException(
            String.format("expected %s to be present", Fields.ALGORITHM_FIELD));
      }
      if (!document.containsKey(Fields.DATE_FIELD)) {
        throw new IllegalStateException(
            String.format("expected %s to be present", Fields.DATE_FIELD));
      }
      if (!document.containsKey(Fields.CREDENTIAL_FIELD)) {
        throw new IllegalStateException(
            String.format("expected %s to be present", Fields.CREDENTIAL_FIELD));
      }
      return new AwsS3SignPolicyResult(
          document.getString(Fields.POLICY_FIELD),
          document.getString(Fields.SIGNATURE_FIELD),
          document.getString(Fields.ALGORITHM_FIELD),
          document.getString(Fields.DATE_FIELD),
          document.getString(Fields.CREDENTIAL_FIELD));
    }
  };

  private static class Fields {
    public static final String POLICY_FIELD = "policy";
    public static final String SIGNATURE_FIELD = "signature";
    public static final String ALGORITHM_FIELD = "algorithm";
    public static final String DATE_FIELD = "date";
    public static final String CREDENTIAL_FIELD = "credential";
  }
}
