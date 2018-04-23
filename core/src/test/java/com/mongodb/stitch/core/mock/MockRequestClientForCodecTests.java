package com.mongodb.stitch.core.mock;

import com.mongodb.stitch.core.internal.net.ContentTypes;
import com.mongodb.stitch.core.internal.net.Headers;
import com.mongodb.stitch.core.internal.net.Response;
import com.mongodb.stitch.core.internal.net.StitchAppRoutes;
import com.mongodb.stitch.core.internal.net.StitchDocRequest;
import com.mongodb.stitch.core.internal.net.StitchRequest;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;

import org.bson.types.ObjectId;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class MockRequestClientForCodecTests extends StitchRequestClient {
    static final Map<String, String> BASE_JSON_HEADERS;
    static {
        final HashMap<String, String> map = new HashMap<>();
        map.put(Headers.CONTENT_TYPE, ContentTypes.APPLICATION_JSON);
        BASE_JSON_HEADERS = map;
    }

    private Function<StitchRequest, Response> handlePrimitiveValueTestRoute =
            (StitchRequest request) -> new Response(
                    200,
                    BASE_JSON_HEADERS,
                    new ByteArrayInputStream("42".getBytes())
            );

    private Function<StitchRequest, Response> handleDocumentValueTestRoute =
            (StitchRequest request) -> new Response(
                    200,
                    BASE_JSON_HEADERS,
                    new ByteArrayInputStream(
                            ("{\"_id\": { \"$oid\": \"" + DOCUMENT_ID + "\" },\"intValue\":42}").getBytes())
            );

    private Function<StitchRequest, Response> handleListValueTestRoute =
            (StitchRequest request) -> new Response(
                    200,
                    BASE_JSON_HEADERS,
                    new ByteArrayInputStream(("[21, \"the meaning of life, the universe, and everything\", 84, 168]").getBytes())
            );

    public static final String DOCUMENT_ID = new ObjectId().toHexString();
    public static final String PRIMITIVE_VALUE_TEST_ROUTE = "http://localhost:8080/primitive_value";
    public static final String DOCUMENT_VALUE_TEST_ROUTE = "http://localhost:8080/document_value";
    public static final String LIST_VALUE_TEST_ROUTE = "http://localhost:8080/list_value";

    public MockRequestClientForCodecTests() {
        super("http://localhost:8080", null);
    }

    private Response handleRequest(StitchRequest request) {
        if (request.path.equals(PRIMITIVE_VALUE_TEST_ROUTE)) {
            return this.handlePrimitiveValueTestRoute.apply(request);
        } else if (request.path.equals(DOCUMENT_VALUE_TEST_ROUTE)) {
            return this.handleDocumentValueTestRoute.apply(request);
        } else if (request.path.equals(LIST_VALUE_TEST_ROUTE)) {
            return this.handleListValueTestRoute.apply(request);
        }

        return null;
    }

    @Override
    public Response doJSONRequestRaw(StitchDocRequest stitchReq) {
        return handleRequest(stitchReq);
    }

    @Override
    public Response doRequest(StitchRequest stitchReq) {
        return handleRequest(stitchReq);
    }
}
