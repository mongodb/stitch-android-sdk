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

package com.mongodb.stitch.core.services.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient;
import com.mongodb.stitch.core.internal.common.BsonUtils;
import com.mongodb.stitch.core.internal.net.Method;
import com.mongodb.stitch.core.internal.net.StitchAuthDocRequest;
import java.util.Arrays;
import java.util.List;
import org.bson.Document;
import org.bson.codecs.Decoder;
import org.bson.codecs.IntegerCodec;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class CoreStitchServiceUnitTests {

  @Test
  public void testCallFunctionInternal() {
    final String serviceName = "svc1";
    final StitchServiceRoutes routes = new StitchServiceRoutes("foo");
    final StitchAuthRequestClient requestClient = Mockito.mock(StitchAuthRequestClient.class);
    final CoreStitchService coreStitchService =
        new CoreStitchServiceImpl(
            requestClient,
            routes,
            serviceName,
            BsonUtils.DEFAULT_CODEC_REGISTRY);

    doReturn(42)
        .when(requestClient)
        .doAuthenticatedJsonRequest(any(), ArgumentMatchers.<Decoder<Integer>>any());
    doReturn(42)
        .when(requestClient)
        .doAuthenticatedJsonRequest(any(), ArgumentMatchers.<Class<Integer>>any(), any());

    final String funcName = "myFunc1";
    final List<Integer> args = Arrays.asList(1, 2, 3);
    final Document expectedRequestDoc = new Document();
    expectedRequestDoc.put("name", funcName);
    expectedRequestDoc.put("service", serviceName);
    expectedRequestDoc.put("arguments", args);

    assertEquals(
        42, (int) coreStitchService.callFunctionInternal(
                funcName, args, null, new IntegerCodec()));
    assertEquals(42, (int) coreStitchService.callFunctionInternal(
            funcName, args, null, Integer.class));

    final ArgumentCaptor<StitchAuthDocRequest> docArgument =
        ArgumentCaptor.forClass(StitchAuthDocRequest.class);
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Decoder<Integer>> decArgument = ArgumentCaptor.forClass(Decoder.class);
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Class<Integer>> clazzArgument = ArgumentCaptor.forClass(Class.class);

    verify(requestClient).doAuthenticatedJsonRequest(docArgument.capture(), decArgument.capture());
    assertEquals(docArgument.getValue().getMethod(), Method.POST);
    assertEquals(docArgument.getValue().getPath(), routes.getFunctionCallRoute());
    assertEquals(docArgument.getValue().getDocument(), expectedRequestDoc);
    assertTrue(decArgument.getValue() instanceof IntegerCodec);

    verify(requestClient)
        .doAuthenticatedJsonRequest(docArgument.capture(), clazzArgument.capture(), any());
    assertEquals(docArgument.getValue().getMethod(), Method.POST);
    assertEquals(docArgument.getValue().getDocument(), expectedRequestDoc);
    assertEquals(docArgument.getValue().getPath(), routes.getFunctionCallRoute());
    assertEquals(clazzArgument.getValue(), Integer.class);
  }
}
