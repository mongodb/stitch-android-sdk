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

package com.mongodb.stitch.core.services.http.internal;

import static com.mongodb.stitch.core.testutils.Assert.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.mongodb.stitch.core.services.http.HttpMethod;
import com.mongodb.stitch.core.services.http.HttpRequest;
import com.mongodb.stitch.core.services.http.HttpResponse;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.codecs.Decoder;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class CoreHttpServiceClientUnitTests {

  @Test
  @SuppressWarnings("unchecked")
  public void testExecute() {
    final CoreStitchServiceClient service = Mockito.mock(CoreStitchServiceClient.class);
    final CoreHttpServiceClient client = new CoreHttpServiceClient(service);

    final String expectedUrl = "http://aol.com";
    final HttpMethod expectedMethod = HttpMethod.DELETE;
    final String expectedAuthUrl = "https://username@password:woo.com";
    final byte[] expectedBody = "hello world!".getBytes(StandardCharsets.UTF_8);
    final Map<String, String> expectedCookies = new HashMap<>();
    final Map<String, String> expectedForm = new HashMap<>();
    final Map<String, Collection<String>> expectedHeaders = new HashMap<>();

    final HttpRequest request = new HttpRequest.Builder()
        .withUrl(expectedUrl)
        .withAuthUrl(expectedAuthUrl)
        .withMethod(expectedMethod)
        .withBody(expectedBody)
        .withCookies(expectedCookies)
        .withEncodeBodyAsJson(false)
        .withFollowRedirects(true)
        .withForm(expectedForm)
        .withHeaders(expectedHeaders)
        .build();

    final HttpResponse response =
        new HttpResponse(
            "OK",
            200,
            304,
            expectedHeaders,
            Collections.emptyMap(),
            "body".getBytes(StandardCharsets.UTF_8));

    doReturn(response)
        .when(service).callFunction(any(), any(), any(Decoder.class));

    final HttpResponse result = client.execute(request);
    assertEquals(result, response);

    final ArgumentCaptor<String> funcNameArg = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<List> funcArgsArg = ArgumentCaptor.forClass(List.class);
    final ArgumentCaptor<Decoder<HttpResponse>> resultClassArg =
        ArgumentCaptor.forClass(Decoder.class);
    verify(service)
        .callFunction(
            funcNameArg.capture(),
            funcArgsArg.capture(),
            resultClassArg.capture());

    assertEquals("delete", funcNameArg.getValue());
    assertEquals(1, funcArgsArg.getValue().size());
    final Document expectedArgs = new Document();
    expectedArgs.put("url", expectedUrl);
    expectedArgs.put("authUrl", expectedAuthUrl);
    expectedArgs.put("headers", expectedHeaders);
    expectedArgs.put("cookies", expectedCookies);
    expectedArgs.put("body", expectedBody);
    expectedArgs.put("encodeBodyAsJSON", false);
    expectedArgs.put("form", expectedForm);
    expectedArgs.put("followRedirects", true);
    assertEquals(expectedArgs, funcArgsArg.getValue().get(0));
    assertEquals(ResultDecoders.httpResponseDecoder, resultClassArg.getValue());

    // Should pass along errors
    doThrow(new IllegalArgumentException("whoops"))
        .when(service).callFunction(any(), any(), any(Decoder.class));
    assertThrows(() -> client.execute(request),
        IllegalArgumentException.class);
  }
}
