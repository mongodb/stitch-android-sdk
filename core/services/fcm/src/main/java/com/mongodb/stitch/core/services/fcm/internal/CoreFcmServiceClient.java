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

import com.mongodb.stitch.core.services.fcm.FcmSendMessageNotification;
import com.mongodb.stitch.core.services.fcm.FcmSendMessageRequest;
import com.mongodb.stitch.core.services.fcm.FcmSendMessageResult;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import java.util.Collection;
import java.util.Collections;
import org.bson.Document;

public class CoreFcmServiceClient {

  private final CoreStitchServiceClient service;

  public CoreFcmServiceClient(final CoreStitchServiceClient service) {
    this.service = service;
  }

  public FcmSendMessageResult sendMessageTo(
      final String to,
      final FcmSendMessageRequest request
  ) {
    final Document args = getSendMessageRequest(request);
    args.put(SendFields.TO_FIELD, to);
    return service.callFunctionInternal(
        SEND_ACTION, Collections.singletonList(args), ResultDecoders.sendMessageResponseDecoder);
  }

  public FcmSendMessageResult sendMessageToUsers(
      final Collection<String> userIds,
      final FcmSendMessageRequest request
  ) {
    final Document args = getSendMessageRequest(request);
    args.put(SendFields.USER_IDS_FIELD, userIds);
    return service.callFunctionInternal(
        SEND_ACTION, Collections.singletonList(args), ResultDecoders.sendMessageResponseDecoder);
  }

  public FcmSendMessageResult sendMessageToRegistrationTokens(
      final Collection<String> registrationTokens,
      final FcmSendMessageRequest request
  ) {
    final Document args = getSendMessageRequest(request);
    args.put(SendFields.REGISTRATION_TOKENS_FIELD, registrationTokens);
    return service.callFunctionInternal(
        SEND_ACTION, Collections.singletonList(args), ResultDecoders.sendMessageResponseDecoder);
  }

  private Document getSendMessageRequest(final FcmSendMessageRequest request) {
    final Document req = new Document();
    req.put(SendFields.PRIORITY_FIELD, request.getPriority().toString());
    if (request.getCollapseKey() != null) {
      req.put(SendFields.COLLAPSE_KEY_FIELD, request.getCollapseKey());
    }
    if (request.getCollapseKey() != null) {
      req.put(SendFields.CONTENT_AVAILABLE_FIELD, request.getContentAvailable());
    }
    if (request.getMutableContent() != null) {
      req.put(SendFields.MUTABLE_CONTENT_FIELD, request.getMutableContent());
    }
    if (request.getTimeToLive() != null) {
      req.put(SendFields.TIME_TO_LIVE_FIELD, request.getTimeToLive());
    }
    if (request.getData() != null) {
      req.put(SendFields.DATA_FIELD, request.getData());
    }
    if (request.getNotification() != null) {
      final FcmSendMessageNotification notification = request.getNotification();
      final Document notifDoc = new Document();
      if (notification.getTitle() != null) {
        notifDoc.put(SendFields.NOTIFICATION_TITLE_FIELD, notification.getTitle());
      }
      if (notification.getBody() != null) {
        notifDoc.put(SendFields.NOTIFICATION_BODY_FIELD, notification.getBody());
      }
      if (notification.getSound() != null) {
        notifDoc.put(SendFields.NOTIFICATION_SOUND_FIELD, notification.getSound());
      }
      if (notification.getClickAction() != null) {
        notifDoc.put(SendFields.NOTIFICATION_CLICK_ACTION_FIELD, notification.getClickAction());
      }
      if (notification.getBodyLockKey() != null) {
        notifDoc.put(SendFields.NOTIFICATION_BODY_LOC_KEY_FIELD, notification.getBodyLockKey());
      }
      if (notification.getBodyLocArgs() != null) {
        notifDoc.put(SendFields.NOTIFICATION_BODY_LOC_ARGS_FIELD, notification.getBodyLocArgs());
      }
      if (notification.getTitleLocKey() != null) {
        notifDoc.put(SendFields.NOTIFICATION_TITLE_LOC_KEY_FIELD, notification.getTitleLocKey());
      }
      if (notification.getTitleLocArgs() != null) {
        notifDoc.put(SendFields.NOTIFICATION_TITLE_LOC_ARGS_FIELD, notification.getTitleLocArgs());
      }
      if (notification.getIcon() != null) {
        notifDoc.put(SendFields.NOTIFICATION_ICON_FIELD, notification.getIcon());
      }
      if (notification.getTag() != null) {
        notifDoc.put(SendFields.NOTIFICATION_TAG_FIELD, notification.getTag());
      }
      if (notification.getColor() != null) {
        notifDoc.put(SendFields.NOTIFICATION_COLOR_FIELD, notification.getColor());
      }
      if (notification.getBadge() != null) {
        notifDoc.put(SendFields.NOTIFICATION_BADGE_FIELD, notification.getBadge());
      }
      req.put(SendFields.NOTIFICATION_FIELD, notifDoc);
    }
    return req;
  }

  private static final String SEND_ACTION = "send";

  private static class SendFields {
    // Target types
    static final String USER_IDS_FIELD = "userIds";
    static final String TO_FIELD = "to";
    static final String REGISTRATION_TOKENS_FIELD = "registrationTokens";

    static final String PRIORITY_FIELD = "priority";
    static final String COLLAPSE_KEY_FIELD = "collapseKey";
    static final String CONTENT_AVAILABLE_FIELD = "contentAvailable";
    static final String MUTABLE_CONTENT_FIELD = "mutableContent";
    static final String TIME_TO_LIVE_FIELD = "timeToLive";
    static final String DATA_FIELD = "data";
    static final String NOTIFICATION_FIELD = "notification";

    // Notification
    static final String NOTIFICATION_TITLE_FIELD = "title";
    static final String NOTIFICATION_BODY_FIELD = "body";
    static final String NOTIFICATION_SOUND_FIELD = "sound";
    static final String NOTIFICATION_CLICK_ACTION_FIELD = "clickAction";
    static final String NOTIFICATION_BODY_LOC_KEY_FIELD = "bodyLocKey";
    static final String NOTIFICATION_BODY_LOC_ARGS_FIELD = "bodyLocArgs";
    static final String NOTIFICATION_TITLE_LOC_KEY_FIELD = "titleLocKey";
    static final String NOTIFICATION_TITLE_LOC_ARGS_FIELD = "titleLocArgs";
    static final String NOTIFICATION_ICON_FIELD = "icon";
    static final String NOTIFICATION_TAG_FIELD = "tag";
    static final String NOTIFICATION_COLOR_FIELD = "color";
    static final String NOTIFICATION_BADGE_FIELD = "badge";
  }
}
