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

import com.mongodb.stitch.core.push.internal.CoreStitchPushClient;

import org.bson.Document;

public final class CoreFcmServicePushClient {

  private final CoreStitchPushClient pushClient;

  public CoreFcmServicePushClient(final CoreStitchPushClient pushClient) {
    this.pushClient = pushClient;
  }

  public void register(final String registrationToken) {
    this.pushClient.registerInternal(
        new Document(RegisterFields.REGISTRATION_TOKEN, registrationToken));
  }

  public void deregister() {
    this.pushClient.deregisterInternal();
  }

  private static class RegisterFields {
    static final String REGISTRATION_TOKEN = "registrationToken";
  }
}
