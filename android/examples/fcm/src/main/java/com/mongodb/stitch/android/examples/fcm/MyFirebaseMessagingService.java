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

package com.mongodb.stitch.android.examples.fcm;

import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;
import org.bson.Document;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

  private static final String TAG = MyFirebaseMessagingService.class.getSimpleName();
  private static final String STITCH_DATA = "stitch.data";
  private static final String STITCH_APP_ID = "stitch.appId";

  @Override
  public void onMessageReceived(final RemoteMessage remoteMessage) {
    final Map<String, String> data = remoteMessage.getData();
    final Document stitchData;
    if (data.containsKey(STITCH_DATA)) {
      stitchData = Document.parse(data.get(STITCH_DATA));
    } else {
      stitchData = null;
    }

    final String appId = data.containsKey(STITCH_APP_ID) ? data.get(STITCH_APP_ID) : "";

    if (stitchData == null) {
      Log.i(TAG, String.format("received an FCM message: %s", remoteMessage.toString()));
    } else {
      Log.i(TAG, String.format(
          "received an FCM message from Stitch(%s): %s", appId, stitchData.toString()));
    }
  }
}
