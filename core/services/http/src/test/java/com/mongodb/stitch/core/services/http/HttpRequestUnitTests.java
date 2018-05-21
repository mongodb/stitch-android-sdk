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

package com.mongodb.stitch.core.services.http;

import static com.mongodb.stitch.core.testutils.Assert.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class HttpRequestUnitTests {

  @Test
  public void testBuilder() {

    // Require at a minimum url and method
    assertThrows(() -> new HttpRequest.Builder().build(), IllegalArgumentException.class);
    assertThrows(() -> new HttpRequest.Builder()
        .withUrl("http://aol.com").build(), IllegalArgumentException.class);
    assertThrows(() -> new HttpRequest.Builder()
        .withMethod(HttpMethod.GET).build(), IllegalArgumentException.class);

    // Minimum satisfied
    final String expectedUrl = "http://aol.com";
    final HttpMethod expectedMethod = HttpMethod.DELETE;
    final HttpRequest request = new HttpRequest.Builder()
        .withUrl(expectedUrl).withMethod(expectedMethod).build();
    assertEquals(expectedUrl, request.getUrl());
    assertEquals(expectedMethod, request.getMethod());
    assertNull(request.getAuthUrl());
    assertNull(request.getBody());
    assertNull(request.getCookies());
    assertNull(request.getEncodeBodyAsJson());
    assertNull(request.getFollowRedirects());
    assertNull(request.getForm());
    assertNull(request.getHeaders());

    final String expectedAuthUrl = "https://username@password:woo.com";
    final byte[] expectedBody = "hello world!".getBytes(StandardCharsets.UTF_8);
    final Map<String, String> expectedCookies = new HashMap<>();
    final Map<String, String> expectedForm = new HashMap<>();
    final Map<String, Collection<String>> expectedHeaders = new HashMap<>();
    final HttpRequest fullRequest = new HttpRequest.Builder()
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
    assertEquals(expectedUrl, fullRequest.getUrl());
    assertEquals(expectedMethod, fullRequest.getMethod());
    assertEquals(expectedAuthUrl, fullRequest.getAuthUrl());
    assertTrue(Arrays.equals(expectedBody, (byte[]) fullRequest.getBody()));
    assertEquals(expectedCookies, fullRequest.getCookies());
    assertEquals(false, fullRequest.getEncodeBodyAsJson());
    assertEquals(true, fullRequest.getFollowRedirects());
    assertEquals(expectedForm, fullRequest.getForm());
    assertEquals(expectedHeaders, fullRequest.getHeaders());
  }
}
