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

public class AwsS3SignPolicyResult {
  private final String policy;
  private final String signature;
  private final String algorithm;
  private final String date;
  private final String credential;

  /**
   * Constructs a new sign policy result.
   *
   * @param policy the description of the policy that has been signed.
   * @param signature the computed signature of the policy.
   * @param algorithm the algorithm used to compute the signature.
   * @param date the date at which the signature was computed.
   * @param credential the credential that should be used when utilizing this signed policy.
   */
  public AwsS3SignPolicyResult(
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
}
