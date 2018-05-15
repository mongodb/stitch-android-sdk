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
    assertEquals(expectedUrl, request.url);
    assertEquals(expectedMethod, request.method);
    assertNull(request.authUrl);
    assertNull(request.body);
    assertNull(request.cookies);
    assertNull(request.encodeBodyAsJson);
    assertNull(request.followRedirects);
    assertNull(request.form);
    assertNull(request.headers);

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
    assertEquals(expectedUrl, fullRequest.url);
    assertEquals(expectedMethod, fullRequest.method);
    assertEquals(expectedAuthUrl, fullRequest.authUrl);
    assertTrue(Arrays.equals(expectedBody, (byte[]) fullRequest.body));
    assertEquals(expectedCookies, fullRequest.cookies);
    assertEquals(false, fullRequest.encodeBodyAsJson);
    assertEquals(true, fullRequest.followRedirects);
    assertEquals(expectedForm, fullRequest.form);
    assertEquals(expectedHeaders, fullRequest.headers);
  }
}
