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

package com.mongodb.stitch.server.services.aws.s3.internal;

import com.mongodb.stitch.core.services.aws.s3.AwsS3PutObjectResult;
import com.mongodb.stitch.core.services.aws.s3.AwsS3SignPolicyResult;
import com.mongodb.stitch.core.services.aws.s3.internal.CoreAwsS3ServiceClient;
import com.mongodb.stitch.server.services.aws.s3.AwsS3ServiceClient;
import java.io.IOException;
import java.io.InputStream;
import javax.annotation.Nonnull;
import org.bson.types.Binary;

/**
 * @deprecated use AwsServiceClient instead.
 */
@Deprecated
public final class AwsS3ServiceClientImpl implements AwsS3ServiceClient {

  private final CoreAwsS3ServiceClient proxy;

  public AwsS3ServiceClientImpl(final CoreAwsS3ServiceClient client) {
    this.proxy = client;
  }

  /**
   * Puts an object.
   *
   * @param bucket the bucket to put the object in.
   * @param key the key (or name) of the object.
   * @param acl the ACL to apply to the object (e.g. private).
   * @param contentType the content type of the object (e.g. application/json).
   * @param body the body of the object.
   * @return the result of the put which contains the location of the object.
   */
  public AwsS3PutObjectResult putObject(
      @Nonnull final String bucket,
      @Nonnull final String key,
      @Nonnull final String acl,
      @Nonnull final String contentType,
      @Nonnull final String body) {
    return proxy.putObject(bucket, key, acl, contentType, body);
  }

  /**
   * Puts an object.
   *
   * @param bucket the bucket to put the object in.
   * @param key the key (or name) of the object.
   * @param acl the ACL to apply to the object (e.g. private).
   * @param contentType the content type of the object (e.g. application/json).
   * @param body the body of the object.
   * @return the result of the put which contains the location of the object.
   */
  public AwsS3PutObjectResult putObject(
      @Nonnull final String bucket,
      @Nonnull final String key,
      @Nonnull final String acl,
      @Nonnull final String contentType,
      @Nonnull final Binary body) {
    return proxy.putObject(bucket, key, acl, contentType, body);
  }

  /**
   * Puts an object.
   *
   * @param bucket the bucket to put the object in.
   * @param key the key (or name) of the object.
   * @param acl the ACL to apply to the object (e.g. private).
   * @param contentType the content type of the object (e.g. application/json).
   * @param body the body of the object.
   * @return the result of the put which contains the location of the object.
   */
  public AwsS3PutObjectResult putObject(
      @Nonnull final String bucket,
      @Nonnull final String key,
      @Nonnull final String acl,
      @Nonnull final String contentType,
      @Nonnull final byte[] body) {
    return proxy.putObject(bucket, key, acl, contentType, body);
  }

  /**
   * Puts an object.
   *
   * @param bucket the bucket to put the object in.
   * @param key the key (or name) of the object.
   * @param acl the ACL to apply to the object (e.g. private).
   * @param contentType the content type of the object (e.g. application/json).
   * @param body the body of the object.
   * @return the result of the put which contains the location of the object.
   */
  public AwsS3PutObjectResult putObject(
      @Nonnull final String bucket,
      @Nonnull final String key,
      @Nonnull final String acl,
      @Nonnull final String contentType,
      @Nonnull final InputStream body) throws IOException {
    return proxy.putObject(bucket, key, acl, contentType, body);
  }

  /**
   * Signs an AWS S3 security policy for a future put object request. This future request would
   * be made outside of the Stitch SDK. This is typically used for large requests that are better
   * sent directly to AWS.
   * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-post-example.html">Uploading a File to Amazon S3 Using HTTP POST</a>
   *
   * @param bucket the bucket to put the future object in.
   * @param key the key (or name) of the future object.
   * @param acl the ACL to apply to the future object (e.g. private).
   * @param contentType the content type of the object (e.g. application/json).
   * @return the signed policy details.
   */
  public AwsS3SignPolicyResult signPolicy(
      @Nonnull final String bucket,
      @Nonnull final String key,
      @Nonnull final String acl,
      @Nonnull final String contentType
  ) {
    return proxy.signPolicy(bucket, key, acl, contentType);
  }
}
