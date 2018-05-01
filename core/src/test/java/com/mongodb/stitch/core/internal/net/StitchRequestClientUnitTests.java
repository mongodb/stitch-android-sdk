package com.mongodb.stitch.core.internal.net;

import com.mongodb.stitch.core.StitchServiceException;
import com.mongodb.stitch.core.internal.common.StitchObjectMapper;

import org.bson.Document;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class StitchRequestClientUnitTests {
    private static final Map<String, String> HEADERS = new HashMap<>();
    private static final Map<String, Object> TEST_DOC = new HashMap<>();
    static {
        HEADERS.put("bar", "baz");
        TEST_DOC.put("qux", "quux");
    }

    private static final String GET_ENDPOINT = "/get";
    private static final String NOT_GET_ENDPOINT = "/notget";
    private static final String BAD_REQUEST_ENDPOINT = "/badreq";

    @Test
    public void testDoRequest() throws Exception {
        final StitchRequestClient stitchRequestClient = new StitchRequestClient(
                "http://domain.com",
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
                    } catch (final Exception e) {
                        fail(e.getMessage());
                        return null;
                    }
                }
        );

        final StitchRequest.Builder builder = new StitchRequest.Builder()
                .withPath(BAD_REQUEST_ENDPOINT)
                .withMethod(Method.GET);

        try {
            stitchRequestClient.doRequest(builder.build());
            fail();
        } catch (final StitchServiceException ignored) {}


        builder.withPath(GET_ENDPOINT);

        final Response response = stitchRequestClient.doRequest(builder.build());

        assertEquals((int)response.statusCode, 200);
        assertEquals(TEST_DOC, StitchObjectMapper.getInstance().readValue(
                response.body,
                Map.class
        ));
    }

    @Test
    public void testDoJSONRequestRaw() throws Exception {
        final StitchRequestClient stitchRequestClient = new StitchRequestClient(
                "http://domain.com",
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
                    } catch (final Exception e) {
                        fail(e.getMessage());
                        return null;
                    }
                }
        );

        final StitchDocRequest.Builder builder = new StitchDocRequest.Builder();
        builder.withPath(BAD_REQUEST_ENDPOINT)
                .withMethod(Method.POST);

        try {
            stitchRequestClient.doJSONRequestRaw(builder.build());
            fail();
        } catch (final NullPointerException ignored) {}

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
