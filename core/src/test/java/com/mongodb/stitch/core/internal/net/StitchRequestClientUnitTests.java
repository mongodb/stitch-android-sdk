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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.mongodb.stitch.core.StitchServiceException;
import com.mongodb.stitch.core.internal.common.StitchObjectMapper;
import com.mongodb.stitch.core.testutil.Constants;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.HashMap;
import java.util.Map;
import org.bson.Document;
import org.junit.Test;

public class StitchRequestClientUnitTests {
  private static final Map<String, String> HEADERS = new HashMap<>();
  private static final Map<String, Object> TEST_DOC = new HashMap<>();
  private static final String GET_ENDPOINT = "/get";
  private static final String NOT_GET_ENDPOINT = "/notget";
  private static final String BAD_REQUEST_ENDPOINT = "/badreq";

  static {
    HEADERS.put("bar", "baz");
    TEST_DOC.put("qux", "quux");
  }

  @Test
  public void testDoRequest() throws Exception {
    final StitchRequestClient stitchRequestClient =
        new StitchRequestClient(
            "http://domain.com",
            (Request request) -> {
              if (request.getUrl().contains(BAD_REQUEST_ENDPOINT)) {
                return new Response(500, HEADERS, null);
              }

              try {
                return new Response(
                    200,
                    HEADERS,
                    new ByteArrayInputStream(
                        StitchObjectMapper.getInstance().writeValueAsBytes(TEST_DOC)));
              } catch (final Exception e) {
                fail(e.getMessage());
                return null;
              }
            }, Constants.DEFAULT_TRANSPORT_TIMEOUT_MILLISECONDS);

    final StitchRequest.Builder builder =
        new StitchRequest.Builder().withPath(BAD_REQUEST_ENDPOINT).withMethod(Method.GET);

    try {
      stitchRequestClient.doRequest(builder.build());
      fail();
    } catch (final StitchServiceException ignored) {
      // do nothing
    }

    builder.withPath(GET_ENDPOINT);

    final Response response = stitchRequestClient.doRequest(builder.build());

    assertEquals((int) response.getStatusCode(), 200);
    assertEquals(
        TEST_DOC, StitchObjectMapper.getInstance().readValue(response.getBody(), Map.class));
  }

  @Test
  public void testDoJsonRequestRaw() throws Exception {
    final StitchRequestClient stitchRequestClient =
        new StitchRequestClient(
            "http://domain.com",
            (Request request) -> {
              if (request.getUrl().contains(BAD_REQUEST_ENDPOINT)) {
                return new Response(500, HEADERS, null);
              }

              try {
                return new Response(200, HEADERS, new ByteArrayInputStream(request.getBody()));
              } catch (final Exception e) {
                fail(e.getMessage());
                return null;
              }
            }, Constants.DEFAULT_TRANSPORT_TIMEOUT_MILLISECONDS);

    final StitchDocRequest.Builder builder = new StitchDocRequest.Builder();
    builder.withPath(BAD_REQUEST_ENDPOINT).withMethod(Method.POST);

    try {
      stitchRequestClient.doJsonRequestRaw(builder.build());
      fail();
    } catch (final NullPointerException ignored) {
      // do nothing
    }

    builder.withPath(NOT_GET_ENDPOINT);
    builder.withDocument(new Document(TEST_DOC));
    final Response response = stitchRequestClient.doJsonRequestRaw(builder.build());

    assertEquals((int) response.getStatusCode(), 200);

    byte[] data = new byte[response.getBody().available()];
    try (final DataInputStream stream = new DataInputStream(response.getBody())) {
      stream.readFully(data);
    }
    assertEquals(
        new Document(TEST_DOC), StitchObjectMapper.getInstance().readValue(data, Document.class));
  }
}
