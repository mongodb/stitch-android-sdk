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

import static com.mongodb.stitch.core.internal.common.IoUtils.readAllToBytes;

import com.mongodb.stitch.core.services.aws.s3.AwsS3PutObjectResult;
import com.mongodb.stitch.core.services.aws.s3.AwsS3SignPolicyResult;
import com.mongodb.stitch.core.services.internal.CoreStitchService;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import org.bson.Document;
import org.bson.types.Binary;

public class CoreAwsS3ServiceClient {

  private final CoreStitchService service;

  protected CoreAwsS3ServiceClient(final CoreStitchService service) {
    this.service = service;
  }

  private AwsS3PutObjectResult putObject(
      final String bucket,
      final String key,
      final String acl,
      final String contentType,
      final Object body
  ) {
    final Document args = new Document();
    args.put(PutAction.BUCKET_PARAM, bucket);
    args.put(PutAction.KEY_PARAM, key);
    args.put(PutAction.ACL_PARAM, acl);
    args.put(PutAction.CONTENT_TYPE_PARAM, contentType);
    args.put(PutAction.BODY_PARAM, body);
    return service.callFunctionInternal(
        PutAction.ACTION_NAME,
        Collections.singletonList(args),
        ResultDecoders.putObjectResultDecoder);
  }

  protected AwsS3PutObjectResult putObjectInternal(
      final String bucket,
      final String key,
      final String acl,
      final String contentType,
      final String body
  ) {
    return putObject(bucket, key, acl, contentType, body);
  }

  protected AwsS3PutObjectResult putObjectInternal(
      final String bucket,
      final String key,
      final String acl,
      final String contentType,
      final Binary body
  ) {
    return putObject(bucket, key, acl, contentType, body);
  }

  protected AwsS3PutObjectResult putObjectInternal(
      final String bucket,
      final String key,
      final String acl,
      final String contentType,
      final byte[] body
  ) {
    return putObjectInternal(bucket, key, acl, contentType, new Binary(body));
  }

  protected AwsS3PutObjectResult putObjectInternal(
      final String bucket,
      final String key,
      final String acl,
      final String contentType,
      final InputStream body
  ) throws IOException {
    return putObjectInternal(bucket, key, acl, contentType, new Binary(readAllToBytes(body)));
  }

  protected AwsS3SignPolicyResult signPolicyInternal(
      final String bucket,
      final String key,
      final String acl,
      final String contentType
  ) {
    final Document args = new Document();
    args.put(SignPolicyAction.BUCKET_PARAM, bucket);
    args.put(SignPolicyAction.KEY_PARAM, key);
    args.put(SignPolicyAction.ACL_PARAM, acl);
    args.put(SignPolicyAction.CONTENT_TYPE_PARAM, contentType);
    return service.callFunctionInternal(
        SignPolicyAction.ACTION_NAME,
        Collections.singletonList(args),
        ResultDecoders.signPolicyResultDecoder);
  }

  private static class PutAction {
    static final String ACTION_NAME = "put";

    static final String BUCKET_PARAM = "bucket";
    static final String KEY_PARAM = "key";
    static final String ACL_PARAM = "acl";
    static final String CONTENT_TYPE_PARAM = "contentType";
    static final String BODY_PARAM = "body";
  }

  private static class SignPolicyAction {
    static final String ACTION_NAME = "signPolicy";

    static final String BUCKET_PARAM = "bucket";
    static final String KEY_PARAM = "key";
    static final String ACL_PARAM = "acl";
    static final String CONTENT_TYPE_PARAM = "contentType";
  }
}
