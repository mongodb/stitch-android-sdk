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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.mongodb.stitch.core.push.internal.CoreStitchPushClient;
import org.bson.Document;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class CoreFcmServicePushClientUnitTests {

  @Test
  public void testRegister() {
    final CoreStitchPushClient coreClient = Mockito.mock(CoreStitchPushClient.class);
    final CoreFcmServicePushClient client = new CoreFcmServicePushClient(coreClient);

    doNothing()
        .when(coreClient).registerInternal(any());

    final String expectedToken = "wooHoo";
    final Document expectedInfo = new Document("registrationToken", expectedToken);
    client.register(expectedToken);

    final ArgumentCaptor<Document> infoArg = ArgumentCaptor.forClass(Document.class);
    verify(coreClient).registerInternal(infoArg.capture());

    assertEquals(expectedInfo, infoArg.getValue());

    // Should pass along errors
    doThrow(new IllegalArgumentException("whoops"))
        .when(coreClient).registerInternal(any());
    assertThrows(() -> {
      client.register(expectedToken);
      return null;
    },IllegalArgumentException.class);
  }

  @Test
  public void testDeregister() {
    final CoreStitchPushClient coreClient = Mockito.mock(CoreStitchPushClient.class);
    final CoreFcmServicePushClient client = new CoreFcmServicePushClient(coreClient);

    doNothing()
        .when(coreClient).deregisterInternal();

    client.deregister();
    verify(coreClient).deregisterInternal();

    // Should pass along errors
    doThrow(new IllegalArgumentException("whoops"))
        .when(coreClient).deregisterInternal();
    assertThrows(() -> {
      client.deregister();
      return null;
    },IllegalArgumentException.class);
  }
}
