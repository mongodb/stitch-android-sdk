package com.mongodb.stitch.core.services.internal;

import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient;
import com.mongodb.stitch.core.internal.common.BSONUtils;
import com.mongodb.stitch.core.internal.net.ContentTypes;
import com.mongodb.stitch.core.internal.net.Headers;
import com.mongodb.stitch.core.internal.net.Method;
import com.mongodb.stitch.core.internal.net.Response;
import com.mongodb.stitch.core.internal.net.StitchAppRoutes;
import com.mongodb.stitch.core.internal.net.StitchAuthDocRequest;
import com.mongodb.stitch.core.internal.net.StitchAuthRequest;

import org.bson.Document;
import org.bson.codecs.Decoder;
import org.bson.codecs.IntegerCodec;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoreStitchServiceUnitTests {
    private static final StitchAppRoutes APP_ROUTES = new StitchAppRoutes("");
    private static final String MOCK_SERVICE_NAME = "mockService";
    private static final String MOCK_FUNCTION_NAME = "mockFunction";
    private static final List<Integer> MOCK_ARGS = Arrays.asList(1, 2, 3);
    private static final Document EXPECTED_DOC;
    static {
        Document document = new Document();
        document.put("name", MOCK_FUNCTION_NAME);
        document.put("service", MOCK_SERVICE_NAME);
        document.put("arguments", MOCK_ARGS);

        EXPECTED_DOC = document;
    }
    private static final Map<String, String> BASE_JSON_HEADERS;
    static {
        final HashMap<String, String> map = new HashMap<>();
        map.put(Headers.CONTENT_TYPE, ContentTypes.APPLICATION_JSON);
        BASE_JSON_HEADERS = map;
    }

    private class MockAuthRequestClient implements StitchAuthRequestClient {
        @Override
        public Response doAuthenticatedRequest(StitchAuthRequest stitchReq) {
            return new Response(200, BASE_JSON_HEADERS, null);
        }

        public <T> T doAuthenticatedJSONRequest(final StitchAuthDocRequest stitchReq, Decoder<T> decoder) {
            assertEquals(stitchReq.method, Method.POST);
            assertEquals(stitchReq.path, APP_ROUTES.getServiceRoutes().getFunctionCallRoute());
            assertEquals(EXPECTED_DOC, stitchReq.document);
            return BSONUtils.parseValue("42", decoder);
        }

        public <T> T doAuthenticatedJSONRequest(final StitchAuthDocRequest stitchReq, Class<T> resultClass) {
            assertEquals(stitchReq.method, Method.POST);
            assertEquals(stitchReq.path, APP_ROUTES.getServiceRoutes().getFunctionCallRoute());
            assertEquals(EXPECTED_DOC, stitchReq.document);
            return BSONUtils.parseValue("42", resultClass);

        }

        @Override
        public Response doAuthenticatedJSONRequestRaw(StitchAuthDocRequest stitchReq) {
            return null;
        }
    }

    @Test
    void testCallFunctionInternal() {
        final CoreStitchService coreStitchService = new CoreStitchService(
                new MockAuthRequestClient(),
                APP_ROUTES.getServiceRoutes(),
                MOCK_SERVICE_NAME
        ) { };

        assertEquals(42, (int) coreStitchService.callFunctionInternal(MOCK_FUNCTION_NAME, MOCK_ARGS, new IntegerCodec()));
        assertEquals(42, (int) coreStitchService.callFunctionInternal(MOCK_FUNCTION_NAME, MOCK_ARGS, Integer.class));
    }
}
