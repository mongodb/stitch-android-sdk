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

package com.mongodb.stitch.core.services.fcm.internal;

import static com.mongodb.stitch.core.testutils.Assert.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mongodb.stitch.core.services.fcm.FcmSendMessageNotification;
import com.mongodb.stitch.core.services.fcm.FcmSendMessagePriority;
import com.mongodb.stitch.core.services.fcm.FcmSendMessageRequest;
import com.mongodb.stitch.core.services.fcm.FcmSendMessageResult;
import com.mongodb.stitch.core.services.fcm.FcmSendMessageResultFailureDetail;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.bson.Document;
import org.bson.codecs.Decoder;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class CoreFcmServiceClientUnitTests {
  private static final String ONE = "one";
  private static final String TWO = "two";
  private static final String THREE = "three";

  @Test
  @SuppressWarnings("unchecked")
  public void testSendMessage() {
    final CoreStitchServiceClient service = Mockito.mock(CoreStitchServiceClient.class);
    final CoreFcmServiceClient client = new CoreFcmServiceClient(service);

    final String collapseKey = "one";
    final boolean contentAvaialble = true;
    final Document data = new Document("hello", "world");
    final boolean mutableContent = true;

    final String badge = "myBadge";
    final String body = "hellllo";
    final String bodyLocArgs = "woo";
    final String bodyLocKey = "hoo";
    final String clickAction = "how";
    final String color = "are";
    final String icon = "you";
    final String sound = "doing";
    final String tag = "today";
    final String title = "my";
    final String titleLocArgs = "good";
    final String titleLocKey = "friend";
    final FcmSendMessageNotification notification = new FcmSendMessageNotification.Builder()
        .withBadge(badge)
        .withBody(body)
        .withBodyLocArgs(bodyLocArgs)
        .withBodyLocKey(bodyLocKey)
        .withClickAction(clickAction)
        .withColor(color)
        .withIcon(icon)
        .withSound(sound)
        .withTag(tag)
        .withTitle(title)
        .withTitleLocArgs(titleLocArgs)
        .withTitleLocKey(titleLocKey)
        .build();

    final FcmSendMessagePriority priority = FcmSendMessagePriority.HIGH;
    final long timeToLive = 10000000000L;

    final FcmSendMessageRequest fullRequest = new FcmSendMessageRequest.Builder()
        .withCollapseKey(collapseKey)
        .withContentAvailable(contentAvaialble)
        .withData(data)
        .withMutableContent(mutableContent)
        .withNotification(notification)
        .withPriority(priority)
        .withTimeToLive(timeToLive)
        .build();

    final List<FcmSendMessageResultFailureDetail> failureDetails = new ArrayList<>();
    failureDetails.add(new FcmSendMessageResultFailureDetail(1, "hello", "world"));
    failureDetails.add(new FcmSendMessageResultFailureDetail(2, "woo", "foo"));
    final FcmSendMessageResult result =
        new FcmSendMessageResult(
            4,
            2,
            failureDetails);

    doReturn(result)
        .when(service).callFunction(any(), any(), any(Decoder.class));

    final String to = "who";
    assertEquals(result, client.sendMessageTo(to, fullRequest));

    final ArgumentCaptor<String> funcNameArg = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<List> funcArgsArg = ArgumentCaptor.forClass(List.class);
    final ArgumentCaptor<Decoder<FcmSendMessageResult>> resultClassArg =
        ArgumentCaptor.forClass(Decoder.class);
    verify(service)
        .callFunction(
            funcNameArg.capture(),
            funcArgsArg.capture(),
            resultClassArg.capture());

    assertEquals("send", funcNameArg.getValue());
    assertEquals(1, funcArgsArg.getValue().size());
    final Document expectedArgs = new Document();
    expectedArgs.put("priority", priority.toString());
    expectedArgs.put("collapseKey", collapseKey);
    expectedArgs.put("contentAvailable", contentAvaialble);
    expectedArgs.put("mutableContent", mutableContent);
    expectedArgs.put("timeToLive", timeToLive);
    expectedArgs.put("data", data);

    final Document expectedNotif = new Document();
    expectedNotif.put("title", title);
    expectedNotif.put("body", body);
    expectedNotif.put("sound", sound);
    expectedNotif.put("clickAction", clickAction);
    expectedNotif.put("bodyLocKey", bodyLocKey);
    expectedNotif.put("bodyLocArgs", bodyLocArgs);
    expectedNotif.put("titleLocKey", titleLocKey);
    expectedNotif.put("titleLocArgs", titleLocArgs);
    expectedNotif.put("icon", icon);
    expectedNotif.put("tag", tag);
    expectedNotif.put("color", color);
    expectedNotif.put("badge", badge);

    expectedArgs.put("notification", expectedNotif);
    expectedArgs.put("to", to);
    assertEquals(expectedArgs, funcArgsArg.getValue().get(0));
    assertEquals(ResultDecoders.sendMessageResponseDecoder, resultClassArg.getValue());

    final Collection<String> registrationTokens = Arrays.asList(ONE, TWO);
    assertEquals(result, client.sendMessageToRegistrationTokens(registrationTokens, fullRequest));

    verify(service, times(2))
        .callFunction(
            funcNameArg.capture(),
            funcArgsArg.capture(),
            resultClassArg.capture());

    assertEquals("send", funcNameArg.getValue());
    assertEquals(1, funcArgsArg.getValue().size());

    expectedArgs.remove("to");
    expectedArgs.put("registrationTokens", registrationTokens);
    assertEquals(expectedArgs, funcArgsArg.getValue().get(0));
    assertEquals(ResultDecoders.sendMessageResponseDecoder, resultClassArg.getValue());

    final Collection<String> userIds = Arrays.asList(TWO, THREE);
    assertEquals(result, client.sendMessageToUsers(userIds, fullRequest));

    verify(service, times(3))
        .callFunction(
            funcNameArg.capture(),
            funcArgsArg.capture(),
            resultClassArg.capture());

    assertEquals("send", funcNameArg.getValue());
    assertEquals(1, funcArgsArg.getValue().size());

    expectedArgs.remove("registrationTokens");
    expectedArgs.put("userIds", userIds);
    assertEquals(expectedArgs, funcArgsArg.getValue().get(0));
    assertEquals(ResultDecoders.sendMessageResponseDecoder, resultClassArg.getValue());

    // Should pass along errors
    doThrow(new IllegalArgumentException("whoops"))
        .when(service).callFunction(any(), any(), any(Decoder.class));
    assertThrows(() -> {
      client.sendMessageTo(
              "who",
              new FcmSendMessageRequest.Builder().build());
      return null;
    },IllegalArgumentException.class);
    assertThrows(() -> {
      client.sendMessageToRegistrationTokens(
          Arrays.asList(ONE, TWO),
          new FcmSendMessageRequest.Builder().build());
      return null;
    },IllegalArgumentException.class);
    assertThrows(() -> {
      client.sendMessageToUsers(
          Arrays.asList(ONE, TWO),
          new FcmSendMessageRequest.Builder().build());
      return null;
    },IllegalArgumentException.class);
  }
}
