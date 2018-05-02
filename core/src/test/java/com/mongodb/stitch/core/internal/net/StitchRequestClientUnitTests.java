package com.mongodb.stitch.core.internal.net;

import com.mongodb.stitch.core.StitchRequestException;
import com.mongodb.stitch.core.StitchServiceException;
import com.mongodb.stitch.core.internal.common.StitchObjectMapper;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StitchRequestClientUnitTests {
    private static final String BASE_URL = "http://localhost:9090";
    private static final String HEADER_KEY = "bar";
    private static final String HEADER_VALUE = "baz";
    private static final Map<String, String> HEADERS;
    static {
        Map<String, String> map = new HashMap<>();
        map.put(HEADER_KEY, HEADER_VALUE);
        HEADERS = map;
    }

    private static final Map<String, Object> TEST_DOC;
    static {
        Map<String, Object> map = new HashMap<>();
        map.put("qux", "quux");
        TEST_DOC = map;
    }

    private static final String GET_ENDPOINT = "/get";
    private static final String NOT_GET_ENDPOINT = "/notget";
    private static final String BAD_REQUEST_ENDPOINT = "/badreq";

    @Test
    void testDoRequest() throws Exception {
        final StitchRequestClient stitchRequestClient = new StitchRequestClient(
                BASE_URL,
                (Request request) -> {
                    if (request.url.contains(BAD_REQUEST_ENDPOINT)) {
                        return new Response(500, HEADERS, null);
                    }

                    try {
                        return new Response(
                                200,
                                HEADERS,
                                new ByteArrayInputStream(
                                        StitchObjectMapper.getInstance().writeValueAsBytes(
                                                TEST_DOC
                                        )
                                )
                        );
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                }
        );

        final StitchRequest.Builder builder = new StitchRequest.Builder()
                .withPath(BAD_REQUEST_ENDPOINT)
                .withMethod(Method.GET);

        assertThrows(
                StitchServiceException.class,
                () -> stitchRequestClient.doRequest(builder.build())
        );


        builder.withPath(GET_ENDPOINT);

        final Response response = stitchRequestClient.doRequest(builder.build());

        assertEquals((int)response.statusCode, 200);
        assertEquals(TEST_DOC, StitchObjectMapper.getInstance().readValue(
                response.body,
                Map.class
        ));
    }

    @Test
    void testDoJSONRequestRaw() throws Exception {
        final StitchRequestClient stitchRequestClient = new StitchRequestClient(
                BASE_URL,
                (Request request) -> {
                    if (request.url.contains(BAD_REQUEST_ENDPOINT)) {
                        return new Response(500, HEADERS, null);
                    }

                    try {
                        return new Response(
                                200,
                                HEADERS,
                                new ByteArrayInputStream(request.body)
                        );
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                }
        );

        final StitchDocRequest.Builder builder = new StitchDocRequest.Builder();
        builder.withPath(BAD_REQUEST_ENDPOINT)
                .withMethod(Method.POST);

        assertThrows(
                NullPointerException.class,
                () -> stitchRequestClient.doJSONRequestRaw(builder.build())
        );

        builder.withPath(NOT_GET_ENDPOINT);
        builder.withDocument(new Document(TEST_DOC));
        final Response response = stitchRequestClient.doJSONRequestRaw(builder.build());

        assertEquals((int)response.statusCode, 200);

        byte[] data = new byte[response.body.available()];
        new DataInputStream(response.body).readFully(data);
        assertEquals(new Document(TEST_DOC),
                StitchObjectMapper.getInstance().readValue(data, Document.class));
    }
}