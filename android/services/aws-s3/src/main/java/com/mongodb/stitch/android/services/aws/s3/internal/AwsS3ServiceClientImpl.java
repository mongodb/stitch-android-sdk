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

package com.mongodb.stitch.android.services.aws.s3.internal;

import android.support.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.android.core.services.internal.StitchService;
import com.mongodb.stitch.android.services.aws.s3.AwsS3ServiceClient;
import com.mongodb.stitch.core.services.aws.s3.AwsS3PutObjectResult;
import com.mongodb.stitch.core.services.aws.s3.AwsS3SignPolicyResult;
import com.mongodb.stitch.core.services.aws.s3.internal.CoreAwsS3ServiceClient;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;
import org.bson.types.Binary;

public final class AwsS3ServiceClientImpl extends CoreAwsS3ServiceClient
    implements AwsS3ServiceClient {

  private final TaskDispatcher dispatcher;

  public AwsS3ServiceClientImpl(final StitchService service, final TaskDispatcher dispatcher) {
    super(service);
    this.dispatcher = dispatcher;
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
  public Task<AwsS3PutObjectResult> putObject(
      @NonNull final String bucket,
      @NonNull final String key,
      @NonNull final String acl,
      @NonNull final String contentType,
      @NonNull final String body) {
    return dispatcher.dispatchTask(new Callable<AwsS3PutObjectResult>() {
      @Override
      public AwsS3PutObjectResult call() {
        return putObjectInternal(bucket, key, acl, contentType, body);
      }
    });
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
  public Task<AwsS3PutObjectResult> putObject(
      @NonNull final String bucket,
      @NonNull final String key,
      @NonNull final String acl,
      @NonNull final String contentType,
      @NonNull final Binary body) {
    return dispatcher.dispatchTask(new Callable<AwsS3PutObjectResult>() {
      @Override
      public AwsS3PutObjectResult call() {
        return putObjectInternal(bucket, key, acl, contentType, body);
      }
    });
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
  public Task<AwsS3PutObjectResult> putObject(
      @NonNull final String bucket,
      @NonNull final String key,
      @NonNull final String acl,
      @NonNull final String contentType,
      @NonNull final byte[] body) {
    return dispatcher.dispatchTask(new Callable<AwsS3PutObjectResult>() {
      @Override
      public AwsS3PutObjectResult call() {
        return putObjectInternal(bucket, key, acl, contentType, body);
      }
    });
  }

  /**
   * Puts an object.
   *
   * @param bucket the bucket to put the object in.
   * @param key the key (or name) of the object.
   * @param acl the ACL to apply to the object (e.g. private).
   * @param contentType the content type of the object (e.g. application/json).
   * @param body the body of the object.
   * @return the result of the put which contains the location of the object. The task will
   *         have an {@link IOException} in the event the body cannot be read.
   */
  public Task<AwsS3PutObjectResult> putObject(
      @NonNull final String bucket,
      @NonNull final String key,
      @NonNull final String acl,
      @NonNull final String contentType,
      @NonNull final InputStream body) {
    return dispatcher.dispatchTask(new Callable<AwsS3PutObjectResult>() {
      @Override
      public AwsS3PutObjectResult call() throws IOException {
        return putObjectInternal(bucket, key, acl, contentType, body);
      }
    });
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
  public Task<AwsS3SignPolicyResult> signPolicy(
      @NonNull final String bucket,
      @NonNull final String key,
      @NonNull final String acl,
      @NonNull final String contentType
  ) {
    return dispatcher.dispatchTask(new Callable<AwsS3SignPolicyResult>() {
      @Override
      public AwsS3SignPolicyResult call() {
        return signPolicyInternal(bucket, key, acl, contentType);
      }
    });
  }
}
