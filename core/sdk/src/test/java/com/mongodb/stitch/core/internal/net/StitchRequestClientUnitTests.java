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

package com.mongodb.stitch.core.internal.net;

import static com.mongodb.stitch.core.testutils.Assert.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.mongodb.stitch.core.StitchRequestErrorCode;
import com.mongodb.stitch.core.StitchRequestException;
import com.mongodb.stitch.core.StitchServiceErrorCode;
import com.mongodb.stitch.core.StitchServiceException;
import com.mongodb.stitch.core.internal.common.StitchObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.bson.Document;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class StitchRequestClientUnitTests {

  @Test
  public void testDoRequest() throws Exception {
    final String domain = "http://domain.com";
    final Transport transport = Mockito.mock(Transport.class);
    final StitchRequestClient stitchRequestClient =
        new StitchRequestClient(domain, transport, 1500L);

    // A bad response should throw an exception
    doReturn(new Response(500)).when(transport).roundTrip(any());
    final ArgumentCaptor<Request> requestArg = ArgumentCaptor.forClass(Request.class);

    final String path = "/path";
    final StitchRequest.Builder builder =
        new StitchRequest.Builder().withPath(path).withMethod(Method.GET);

    assertThrows(
        () -> stitchRequestClient.doRequest(builder.build()),
        StitchServiceException.class);
    verify(transport).roundTrip(requestArg.capture());
    final Request actualRequest = requestArg.getValue();
    final Request expectedRequest = new Request.Builder()
        .withMethod(Method.GET)
        .withUrl(URI.create(domain).resolve(path).toString())
        .build();
    assertEquals(expectedRequest, actualRequest);

    // A normal response should be able to be decoded
    doReturn(
        new Response(200, "{\"hello\": \"world\", \"a\": 42}"))
        .when(transport).roundTrip(any());
    final Response response = stitchRequestClient.doRequest(builder.build());

    assertEquals(response.getStatusCode(), 200);
    final Map<String, Object> expected = new HashMap<>();
    expected.put("hello", "world");
    expected.put("a", 42);
    assertEquals(
        expected,
        StitchObjectMapper.getInstance().readValue(response.getBody(), Map.class));

    // Error responses should be handled
    doReturn(new Response(500)).when(transport).roundTrip(any());
    try {
      stitchRequestClient.doRequest(builder.build());
      fail();
    } catch (final StitchServiceException ex) {
      assertEquals(ex.getErrorCode(), StitchServiceErrorCode.UNKNOWN);
      assertTrue(ex.getMessage().contains("received unexpected status"));
    }

    doReturn(new Response(500, "whoops")).when(transport).roundTrip(any());
    try {
      stitchRequestClient.doRequest(builder.build());
      fail();
    } catch (final StitchServiceException ex) {
      assertEquals(ex.getErrorCode(), StitchServiceErrorCode.UNKNOWN);
      assertTrue(ex.getMessage().equals("whoops"));
    }

    final Map<String, String> headers = new HashMap<>();
    headers.put(Headers.CONTENT_TYPE, ContentTypes.APPLICATION_JSON);

    doReturn(new Response(500, headers,"whoops")).when(transport).roundTrip(any());
    try {
      stitchRequestClient.doRequest(builder.build());
      fail();
    } catch (final StitchServiceException ex) {
      assertEquals(ex.getErrorCode(), StitchServiceErrorCode.UNKNOWN);
      assertTrue(ex.getMessage().equals("whoops"));
    }

    doReturn(
        new Response(500, headers,"{\"error\": \"bad\", \"error_code\": \"InvalidSession\"}"))
        .when(transport).roundTrip(any());
    try {
      stitchRequestClient.doRequest(builder.build());
      fail();
    } catch (final StitchServiceException ex) {
      assertEquals(ex.getErrorCode(), StitchServiceErrorCode.INVALID_SESSION);
      assertTrue(ex.getMessage().equals("bad"));
    }

    // Handles round trip failing
    doThrow(new IllegalStateException("whoops")).when(transport).roundTrip(any());
    try {
      stitchRequestClient.doRequest(builder.build());
      fail();
    } catch (final StitchRequestException ex) {
      assertEquals(ex.getErrorCode(), StitchRequestErrorCode.TRANSPORT_ERROR);
    }
  }

  @Test
  public void testDoJsonRequestWithDoc() throws Exception {
    final String domain = "http://domain.com";
    final Transport transport = Mockito.mock(Transport.class);
    final StitchRequestClient stitchRequestClient =
        new StitchRequestClient(domain, transport, 1500L);

    final String path = "/path";
    final Document document = new Document("my", 24);
    final StitchDocRequest.Builder builder = new StitchDocRequest.Builder();
    builder.withDocument(document);
    builder.withPath(path).withMethod(Method.PATCH);

    // A bad response should throw an exception
    doReturn(new Response(500)).when(transport).roundTrip(any());
    final ArgumentCaptor<Request> requestArg = ArgumentCaptor.forClass(Request.class);

    assertThrows(
        () -> stitchRequestClient.doRequest(builder.build()), StitchServiceException.class);
    verify(transport).roundTrip(requestArg.capture());
    final Request actualRequest = requestArg.getValue();
    final Map<String, String> expectedHeaders = new HashMap<>();
    expectedHeaders.put(Headers.CONTENT_TYPE, ContentTypes.APPLICATION_JSON);
    final Request expectedRequest = new Request.Builder()
        .withMethod(Method.PATCH)
        .withUrl(URI.create(domain).resolve(path).toString())
        .withBody("{\"my\" : {\"$numberInt\" : \"24\"}}".getBytes(StandardCharsets.UTF_8))
        .withHeaders(expectedHeaders)
        .build();
    assertEquals(expectedRequest, actualRequest);

    // A normal response should be able to be decoded
    doReturn(
        new Response(200, "{\"hello\": \"world\", \"a\": 42}"))
        .when(transport).roundTrip(any());
    final Response response = stitchRequestClient.doRequest(builder.build());

    assertEquals(response.getStatusCode(), 200);
    final Map<String, Object> expected = new HashMap<>();
    expected.put("hello", "world");
    expected.put("a", 42);
    assertEquals(
        expected,
        StitchObjectMapper.getInstance().readValue(response.getBody(), Map.class));

    // Error responses should be handled
    doReturn(new Response(500)).when(transport).roundTrip(any());
    try {
      stitchRequestClient.doRequest(builder.build());
      fail();
    } catch (final StitchServiceException ex) {
      assertEquals(ex.getErrorCode(), StitchServiceErrorCode.UNKNOWN);
      assertTrue(ex.getMessage().contains("received unexpected status"));
    }

    doReturn(new Response(500, "whoops")).when(transport).roundTrip(any());
    try {
      stitchRequestClient.doRequest(builder.build());
      fail();
    } catch (final StitchServiceException ex) {
      assertEquals(ex.getErrorCode(), StitchServiceErrorCode.UNKNOWN);
      assertTrue(ex.getMessage().equals("whoops"));
    }

    final Map<String, String> headers = new HashMap<>();
    headers.put(Headers.CONTENT_TYPE, ContentTypes.APPLICATION_JSON);

    doReturn(new Response(500, headers,"whoops")).when(transport).roundTrip(any());
    try {
      stitchRequestClient.doRequest(builder.build());
      fail();
    } catch (final StitchServiceException ex) {
      assertEquals(ex.getErrorCode(), StitchServiceErrorCode.UNKNOWN);
      assertTrue(ex.getMessage().equals("whoops"));
    }

    doReturn(
        new Response(500, headers,"{\"error\": \"bad\", \"error_code\": \"InvalidSession\"}"))
        .when(transport).roundTrip(any());
    try {
      stitchRequestClient.doRequest(builder.build());
      fail();
    } catch (final StitchServiceException ex) {
      assertEquals(ex.getErrorCode(), StitchServiceErrorCode.INVALID_SESSION);
      assertTrue(ex.getMessage().equals("bad"));
    }

    // Handles round trip failing
    doThrow(new IllegalStateException("whoops")).when(transport).roundTrip(any());
    try {
      stitchRequestClient.doRequest(builder.build());
      fail();
    } catch (final StitchRequestException ex) {
      assertEquals(ex.getErrorCode(), StitchRequestErrorCode.TRANSPORT_ERROR);
    }
  }

  @Test
  public void testHandleNonCanonicalHeaders() throws Exception {
    final String domain = "http://domain.com";
    final Transport transport = Mockito.mock(Transport.class);
    final StitchRequestClient stitchRequestClient = new StitchRequestClient(domain, transport, 1500L);

    // A bad response should throw an exception
    doReturn(new Response(500)).when(transport).roundTrip(any());

    final String path = "/path";
    final StitchRequest.Builder builder =
        new StitchRequest.Builder().withPath(path).withMethod(Method.GET);

    final Map<String, String> headers = new HashMap<>();
    headers.put(Headers.CONTENT_TYPE, ContentTypes.APPLICATION_JSON);

    doReturn(
        new Response(500, headers,"{\"error\": \"bad\", \"error_code\": \"InvalidSession\"}"))
        .when(transport).roundTrip(any());
    try {
      stitchRequestClient.doRequest(builder.build());
      fail();
    } catch (final StitchServiceException ex) {
      assertEquals(ex.getErrorCode(), StitchServiceErrorCode.INVALID_SESSION);
      assertTrue(ex.getMessage().equals("bad"));
    }

    headers.put(Headers.CONTENT_TYPE_CANON, ContentTypes.APPLICATION_JSON);
    doReturn(
        new Response(500, headers,"{\"error\": \"bad\", \"error_code\": \"InvalidSession\"}"))
        .when(transport).roundTrip(any());
    try {
      stitchRequestClient.doRequest(builder.build());
      fail();
    } catch (final StitchServiceException ex) {
      assertEquals(ex.getErrorCode(), StitchServiceErrorCode.INVALID_SESSION);
      assertTrue(ex.getMessage().equals("bad"));
    }
  }

  @Test
  public void testDoRequestWithTimeout() throws Exception {
    final String domain = "http://domain.com";
    final Transport transport = Mockito.mock(Transport.class);
    final StitchRequestClient stitchRequestClient = new StitchRequestClient(domain, transport, 1500L);

    doThrow(new TimeoutException("whoops")).when(transport)
        .roundTrip(ArgumentMatchers.argThat(req -> req.getTimeout() == 3000L));
    doReturn(new Response(503)).when(transport)
        .roundTrip(ArgumentMatchers.argThat(req -> req.getTimeout() != 3000L));

    final StitchRequest.Builder builder = new StitchRequest.Builder()
        .withPath("/path")
        .withMethod(Method.GET)
        .withTimeout(3000L);

    try {
      stitchRequestClient.doRequest(builder.build());
      fail();
    } catch (final StitchRequestException ignored) {
      assertEquals(ignored.getErrorCode(), StitchRequestErrorCode.TRANSPORT_ERROR);
    }
  }
}
