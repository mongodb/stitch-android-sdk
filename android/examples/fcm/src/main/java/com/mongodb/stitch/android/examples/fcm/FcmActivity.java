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

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.FirebaseMessaging;
import com.mongodb.stitch.android.core.Stitch;
import com.mongodb.stitch.android.core.StitchAppClient;
import com.mongodb.stitch.android.core.auth.StitchUser;
import com.mongodb.stitch.android.services.fcm.FcmServiceClient;
import com.mongodb.stitch.android.services.fcm.FcmServicePushClient;
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential;
import com.mongodb.stitch.core.services.fcm.FcmSendMessageNotification;
import com.mongodb.stitch.core.services.fcm.FcmSendMessagePriority;
import com.mongodb.stitch.core.services.fcm.FcmSendMessageRequest;
import java.util.Collections;
import java.util.Objects;
import org.bson.Document;

public class FcmActivity extends AppCompatActivity {

  private static final String TAG = FcmActivity.class.getSimpleName();
  private static final String GOOGLE_APP_ID = "<GOOGLE_APP_ID>";
  private static final String FCM_SENDER_ID = "<FCM_SENDER_ID>";
  private static final String GCM_SERVICE_NAME = "gcm";

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_fcm);
    toggleDisabled();

    final FirebaseOptions fbOpts = new FirebaseOptions.Builder()
        .setApplicationId(GOOGLE_APP_ID)
        .setGcmSenderId(FCM_SENDER_ID)
        .build();
    FirebaseApp.initializeApp(this, fbOpts);

    // get a client
    final StitchAppClient client = Stitch.getDefaultAppClient();

    // authenticate
    client.getAuth().loginWithCredential(new AnonymousCredential())
        .addOnCompleteListener(new OnCompleteListener<StitchUser>() {
          @Override
          public void onComplete(@NonNull final Task<StitchUser> task) {
            if (!task.isSuccessful()) {
              Log.e(TAG, "failed to log in", task.getException());
              return;
            }
            toggleDeregistered();
          }
        });
  }

  private void toggleRegistered() {
    findViewById(R.id.registerButton).setEnabled(false);
    findViewById(R.id.deregisterButton).setEnabled(true);
    findViewById(R.id.subscribeToNewsButton).setEnabled(true);
    findViewById(R.id.sendToNewsButton).setEnabled(true);
    findViewById(R.id.sendToSelfButton).setEnabled(true);
  }

  private void toggleDisabled() {
    findViewById(R.id.registerButton).setEnabled(false);
    findViewById(R.id.deregisterButton).setEnabled(false);
    findViewById(R.id.subscribeToNewsButton).setEnabled(false);
    findViewById(R.id.sendToNewsButton).setEnabled(false);
    findViewById(R.id.sendToSelfButton).setEnabled(false);
  }

  private void toggleDeregistered() {
    findViewById(R.id.registerButton).setEnabled(true);
    findViewById(R.id.deregisterButton).setEnabled(false);
    findViewById(R.id.subscribeToNewsButton).setEnabled(false);
    findViewById(R.id.sendToNewsButton).setEnabled(false);
    findViewById(R.id.sendToSelfButton).setEnabled(false);
  }

  public void register(final View view) {
    toggleDisabled();

    // get a client
    final StitchAppClient client = Stitch.getDefaultAppClient();

    // get the push client
    final FcmServicePushClient pushClient =
        client.getPush().getClient(FcmServicePushClient.factory, GCM_SERVICE_NAME);

    pushClient.register(FirebaseInstanceId.getInstance().getToken())
        .addOnCompleteListener(new OnCompleteListener<Void>() {
          @Override
          public void onComplete(@NonNull final Task<Void> task) {
            if (!task.isSuccessful()) {
              Log.e(TAG, "failed to register for push notifications", task.getException());
              toggleDeregistered();
              return;
            }
            Log.i("stitch", "registered for push notifications!");
            toggleRegistered();
          }
        });
  }

  public void deregister(final View view) {
    toggleDisabled();

    // get a client
    final StitchAppClient client = Stitch.getDefaultAppClient();

    // get the push client
    final FcmServicePushClient pushClient =
        client.getPush().getClient(FcmServicePushClient.factory, GCM_SERVICE_NAME);

    pushClient.deregister().addOnCompleteListener(new OnCompleteListener<Void>() {
      @Override
      public void onComplete(@NonNull final Task<Void> task) {
        if (!task.isSuccessful()) {
          Log.e(TAG, "failed to deregister for push notifications", task.getException());
          toggleRegistered();
          return;
        }
        toggleDeregistered();
      }
    });
  }

  public void subscribeToNews(final View view) {
    FirebaseMessaging.getInstance().subscribeToTopic("news");
  }

  public void sendToNews(final View view) {
    // get a client
    final StitchAppClient client = Stitch.getDefaultAppClient();
    final FcmServiceClient fcmClient = client.getServiceClient(FcmServiceClient.factory,
        GCM_SERVICE_NAME);
    fcmClient.sendMessageTo("/topics/news", new FcmSendMessageRequest.Builder()
        .withPriority(FcmSendMessagePriority.HIGH)
        .withNotification(new FcmSendMessageNotification.Builder()
            .withTitle("BREAKING NEWS")
            .build())
        .build());
  }

  public void sendToSelf(final View view) {
    // get a client
    final StitchAppClient client = Stitch.getDefaultAppClient();
    final FcmServiceClient fcmClient = client.getServiceClient(FcmServiceClient.factory,
        GCM_SERVICE_NAME);
    fcmClient.sendMessageToUsers(
        Collections.singletonList(Objects.requireNonNull(client.getAuth().getUser()).getId()),
        new FcmSendMessageRequest.Builder()
            .withData(new Document("complex", new Document("value", 42)))
            .build());
  }
}
