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

package com.mongodb.stitch.core.services.http.aws;

import static com.mongodb.stitch.core.testutils.Assert.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mongodb.stitch.core.services.aws.AwsRequest;
import com.mongodb.stitch.core.services.aws.internal.CoreAwsServiceClient;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import java.util.List;
import org.bson.Document;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class CoreAwsServiceClientUnitTests {

  @Test
  @SuppressWarnings("unchecked")
  public void testExecute() {
    final CoreStitchServiceClient service = Mockito.mock(CoreStitchServiceClient.class);
    final CoreAwsServiceClient client = new CoreAwsServiceClient(service);

    final String expectedService = "ses";
    final String expectedAction = "send";
    final String expectedRegion = "us-east-1";
    final Document expectedArguments = new Document("hi", "hello");

    final AwsRequest request = new AwsRequest.Builder()
        .withService(expectedService)
        .withAction(expectedAction)
        .withRegion(expectedRegion)
        .withArguments(expectedArguments)
        .build();

    final Document response = new Document("email", "sent");

    doReturn(response)
        .when(service).callFunction(any(), any(), any(Class.class));

    final Document result = client.execute(request, Document.class);
    assertEquals(result, response);

    final ArgumentCaptor<String> funcNameArg = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<List> funcArgsArg = ArgumentCaptor.forClass(List.class);
    final ArgumentCaptor<Class> resultClassArg =
        ArgumentCaptor.forClass(Class.class);
    verify(service)
        .callFunction(
            funcNameArg.capture(),
            funcArgsArg.capture(),
            resultClassArg.capture());

    assertEquals("execute", funcNameArg.getValue());
    assertEquals(1, funcArgsArg.getValue().size());
    final Document expectedArgs = new Document();
    expectedArgs.put("aws_service", expectedService);
    expectedArgs.put("aws_action", expectedAction);
    expectedArgs.put("aws_region", expectedRegion);
    expectedArgs.put("aws_arguments", expectedArguments);
    assertEquals(expectedArgs, funcArgsArg.getValue().get(0));
    assertEquals(Document.class, resultClassArg.getValue());

    verify(service)
        .callFunction(
            funcNameArg.capture(),
            funcArgsArg.capture(),
            resultClassArg.capture());

    final AwsRequest request2 = new AwsRequest.Builder()
        .withService(expectedService)
        .withAction(expectedAction)
        .build();

    final Document result2 = client.execute(request2, Document.class);
    assertEquals(result2, response);

    verify(service, times(2))
        .callFunction(
            funcNameArg.capture(),
            funcArgsArg.capture(),
            resultClassArg.capture());

    assertEquals("execute", funcNameArg.getValue());
    assertEquals(1, funcArgsArg.getValue().size());
    final Document expectedArgs2 = new Document();
    expectedArgs2.put("aws_service", expectedService);
    expectedArgs2.put("aws_action", expectedAction);
    expectedArgs2.put("aws_arguments", new Document());
    assertEquals(expectedArgs2, funcArgsArg.getValue().get(0));
    assertEquals(Document.class, resultClassArg.getValue());

    // Should pass along errors
    doThrow(new IllegalArgumentException("whoops"))
        .when(service).callFunction(any(), any(), any(Class.class));
    assertThrows(() -> client.execute(request, Document.class),
        IllegalArgumentException.class);
  }
}
