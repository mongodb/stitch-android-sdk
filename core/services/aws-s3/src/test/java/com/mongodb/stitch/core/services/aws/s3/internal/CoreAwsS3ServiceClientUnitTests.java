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

import static com.mongodb.stitch.core.testutils.Assert.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mongodb.stitch.core.services.aws.s3.AwsS3PutObjectResult;
import com.mongodb.stitch.core.services.aws.s3.AwsS3SignPolicyResult;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bson.Document;
import org.bson.codecs.Decoder;
import org.bson.types.Binary;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class CoreAwsS3ServiceClientUnitTests {

  @Test
  @SuppressWarnings("unchecked")
  public void testPutObject() throws IOException {
    final CoreStitchServiceClient service = Mockito.mock(CoreStitchServiceClient.class);
    final CoreAwsS3ServiceClient client = new CoreAwsS3ServiceClient(service);

    final String bucket = "stuff";
    final String key = "myFile";
    final String acl = "public-read";
    final String contentType = "plain/text";
    final String body = "some data yo";

    final String expectedLocation = "awsLocation";

    Mockito.doReturn(new AwsS3PutObjectResult(expectedLocation))
        .when(service).callFunction(any(), any(), any(Decoder.class));

    AwsS3PutObjectResult result =
        client.putObject(bucket, key, acl, contentType, body);
    assertEquals(result.getLocation(), expectedLocation);

    final ArgumentCaptor<String> funcNameArg = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<List> funcArgsArg = ArgumentCaptor.forClass(List.class);
    final ArgumentCaptor<Decoder<AwsS3PutObjectResult>> resultClassArg =
        ArgumentCaptor.forClass(Decoder.class);
    verify(service)
        .callFunction(
            funcNameArg.capture(),
            funcArgsArg.capture(),
            resultClassArg.capture());

    assertEquals("put", funcNameArg.getValue());
    assertEquals(1, funcArgsArg.getValue().size());
    final Document expectedArgs = new Document();
    expectedArgs.put("bucket", bucket);
    expectedArgs.put("key", key);
    expectedArgs.put("acl", acl);
    expectedArgs.put("contentType", contentType);
    expectedArgs.put("body", body);
    assertEquals(expectedArgs, funcArgsArg.getValue().get(0));
    assertEquals(ResultDecoders.putObjectResultDecoder, resultClassArg.getValue());

    final Binary bodyBin = new Binary("hello".getBytes(StandardCharsets.UTF_8));
    result =
        client.putObject(bucket, key, acl, contentType, bodyBin);
    assertEquals(result.getLocation(), expectedLocation);

    verify(service, times(2))
        .callFunction(
            funcNameArg.capture(),
            funcArgsArg.capture(),
            resultClassArg.capture());

    assertEquals("put", funcNameArg.getValue());
    assertEquals(1, funcArgsArg.getValue().size());
    expectedArgs.put("body", bodyBin);
    assertEquals(expectedArgs, funcArgsArg.getValue().get(0));
    assertEquals(ResultDecoders.putObjectResultDecoder, resultClassArg.getValue());

    result =
        client.putObject(bucket, key, acl, contentType, bodyBin.getData());
    assertEquals(result.getLocation(), expectedLocation);

    verify(service, times(3))
        .callFunction(
            funcNameArg.capture(),
            funcArgsArg.capture(),
            resultClassArg.capture());

    assertEquals("put", funcNameArg.getValue());
    assertEquals(1, funcArgsArg.getValue().size());
    assertEquals(expectedArgs, funcArgsArg.getValue().get(0));
    assertEquals(ResultDecoders.putObjectResultDecoder, resultClassArg.getValue());

    final InputStream bodyInput = new ByteArrayInputStream(bodyBin.getData());
    result =
        client.putObject(bucket, key, acl, contentType, bodyInput);
    assertEquals(result.getLocation(), expectedLocation);

    verify(service, times(4))
        .callFunction(
            funcNameArg.capture(),
            funcArgsArg.capture(),
            resultClassArg.capture());

    assertEquals("put", funcNameArg.getValue());
    assertEquals(1, funcArgsArg.getValue().size());
    assertEquals(expectedArgs, funcArgsArg.getValue().get(0));
    assertEquals(ResultDecoders.putObjectResultDecoder, resultClassArg.getValue());

    // Should pass along errors
    doThrow(new IllegalArgumentException("whoops"))
        .when(service).callFunction(any(), any(), any(Decoder.class));
    assertThrows(() -> client.putObject(bucket, key, acl, contentType, body),
        IllegalArgumentException.class);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testSignPolicy() {
    final CoreStitchServiceClient service = Mockito.mock(CoreStitchServiceClient.class);
    final CoreAwsS3ServiceClient client = new CoreAwsS3ServiceClient(service);

    final String bucket = "stuff";
    final String key = "myFile";
    final String acl = "public-read";
    final String contentType = "plain/text";

    final String expectedPolicy = "you shall not";
    final String expectedSignature = "yoursTruly";
    final String expectedAlgorithm = "DES-111";
    final String expectedDate = "01-101-2012";
    final String expectedCredential = "someCredential";

    Mockito.doReturn(new AwsS3SignPolicyResult(
        expectedPolicy,
        expectedSignature,
        expectedAlgorithm,
        expectedDate,
        expectedCredential))
        .when(service).callFunction(any(), any(), any(Decoder.class));

    final AwsS3SignPolicyResult result =
        client.signPolicy(bucket, key, acl, contentType);
    assertEquals(result.getPolicy(), expectedPolicy);
    assertEquals(result.getSignature(), expectedSignature);
    assertEquals(result.getAlgorithm(), expectedAlgorithm);
    assertEquals(result.getDate(), expectedDate);
    assertEquals(result.getCredential(), expectedCredential);

    final ArgumentCaptor<String> funcNameArg = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<List> funcArgsArg = ArgumentCaptor.forClass(List.class);
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Decoder<AwsS3SignPolicyResult>> resultClassArg =
        ArgumentCaptor.forClass(Decoder.class);
    verify(service)
        .callFunction(
            funcNameArg.capture(),
            funcArgsArg.capture(),
            resultClassArg.capture());

    assertEquals("signPolicy", funcNameArg.getValue());
    assertEquals(1, funcArgsArg.getValue().size());
    final Document expectedArgs = new Document();
    expectedArgs.put("bucket", bucket);
    expectedArgs.put("key", key);
    expectedArgs.put("acl", acl);
    expectedArgs.put("contentType", contentType);
    assertEquals(expectedArgs, funcArgsArg.getValue().get(0));
    assertEquals(ResultDecoders.signPolicyResultDecoder, resultClassArg.getValue());

    // Should pass along errors
    doThrow(new IllegalArgumentException("whoops"))
        .when(service).callFunction(any(), any(), any(Decoder.class));
    assertThrows(() -> client.signPolicy(bucket, key, acl, contentType),
        IllegalArgumentException.class);
  }
}
