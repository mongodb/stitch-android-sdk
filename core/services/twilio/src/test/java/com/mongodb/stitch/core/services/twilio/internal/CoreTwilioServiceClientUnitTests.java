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

package com.mongodb.stitch.core.services.twilio.internal;

import static com.mongodb.stitch.core.testutils.Assert.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;

import java.util.List;
import org.bson.Document;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class CoreTwilioServiceClientUnitTests {

  @Test
  public void testSendMessage() {
    final CoreStitchServiceClient service = Mockito.mock(CoreStitchServiceClient.class);
    final CoreTwilioServiceClient client = new CoreTwilioServiceClient(service);

    final String to = "+15558509552";
    final String from = "+15558675309";
    final String body = "I've got your number";

    client.sendMessage(to, from, body);

    final ArgumentCaptor<String> funcNameArg = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<List> funcArgsArg = ArgumentCaptor.forClass(List.class);
    verify(service).callFunction(funcNameArg.capture(), funcArgsArg.capture());

    assertEquals("send", funcNameArg.getValue());
    assertEquals(1, funcArgsArg.getValue().size());
    final Document expectedArgs = new Document();
    expectedArgs.put("to", to);
    expectedArgs.put("from", from);
    expectedArgs.put("body", body);
    assertEquals(expectedArgs, funcArgsArg.getValue().get(0));

    // Should pass along errors
    doThrow(new IllegalArgumentException("whoops"))
        .when(service).callFunction(any(), any());
    assertThrows(() -> {
      client.sendMessage(to, from, body);
      return null;
    }, IllegalArgumentException.class);
  }

  @Test
  public void testSendMessageWithMedia() {
    final CoreStitchServiceClient service = Mockito.mock(CoreStitchServiceClient.class);
    final CoreTwilioServiceClient client = new CoreTwilioServiceClient(service);

    final String to = "+15558509552";
    final String from = "+15558675309";
    final String body = "I've got it!";
    final String mediaUrl = "https://jpegs.com/myjpeg.gif.png";

    client.sendMessage(to, from, body, mediaUrl);

    final ArgumentCaptor<String> funcNameArg = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<List> funcArgsArg = ArgumentCaptor.forClass(List.class);
    verify(service).callFunction(funcNameArg.capture(), funcArgsArg.capture());

    assertEquals("send", funcNameArg.getValue());
    assertEquals(1, funcArgsArg.getValue().size());
    final Document expectedArgs = new Document();
    expectedArgs.put("to", to);
    expectedArgs.put("from", from);
    expectedArgs.put("body", body);
    expectedArgs.put("mediaUrl", mediaUrl);
    assertEquals(expectedArgs, funcArgsArg.getValue().get(0));

    // Should pass along errors
    doThrow(new IllegalArgumentException("whoops"))
        .when(service).callFunction(any(), any());
    assertThrows(() -> {
      client.sendMessage(to, from, body);
      return null;
    }, IllegalArgumentException.class);
  }
}
