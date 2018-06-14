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

package com.mongodb.stitch.core.services.fcm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.bson.Document;
import org.junit.Test;

public class FcmSendMessageRequestUnitTests {

  @Test
  public void testBuilder() {

    // Minimum satisfied
    final FcmSendMessageRequest request = new FcmSendMessageRequest.Builder().build();
    assertEquals(FcmSendMessagePriority.NORMAL, request.getPriority());
    assertNull(request.getCollapseKey());
    assertNull(request.getContentAvailable());
    assertNull(request.getData());
    assertNull(request.getMutableContent());
    assertNull(request.getNotification());
    assertNull(request.getTimeToLive());

    // Fully specified
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

    assertEquals(collapseKey, fullRequest.getCollapseKey());
    assertEquals(contentAvaialble, fullRequest.getContentAvailable());
    assertEquals(data, fullRequest.getData());
    assertEquals(mutableContent, fullRequest.getMutableContent());
    assertEquals(notification, fullRequest.getNotification());
    assertEquals(badge, fullRequest.getNotification().getBadge());
    assertEquals(body, fullRequest.getNotification().getBody());
    assertEquals(bodyLocArgs, fullRequest.getNotification().getBodyLocArgs());
    assertEquals(bodyLocKey, fullRequest.getNotification().getBodyLocKey());
    assertEquals(clickAction, fullRequest.getNotification().getClickAction());
    assertEquals(color, fullRequest.getNotification().getColor());
    assertEquals(icon, fullRequest.getNotification().getIcon());
    assertEquals(sound, fullRequest.getNotification().getSound());
    assertEquals(tag, fullRequest.getNotification().getTag());
    assertEquals(title, fullRequest.getNotification().getTitle());
    assertEquals(titleLocArgs, fullRequest.getNotification().getTitleLocArgs());
    assertEquals(titleLocKey, fullRequest.getNotification().getTitleLocKey());
    assertEquals(priority, fullRequest.getPriority());
    assertEquals((Long) timeToLive, fullRequest.getTimeToLive());
  }
}
