package com.mongodb.stitch.core.auth.internal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.stitch.core.StitchRequestException;
import com.mongodb.stitch.core.StitchServiceErrorCode;
import com.mongodb.stitch.core.StitchServiceException;
import com.mongodb.stitch.core.auth.internal.models.APIAuthInfo;
import com.mongodb.stitch.core.internal.common.BSONUtils;
import com.mongodb.stitch.core.internal.common.MemoryStorage;
import com.mongodb.stitch.core.internal.net.ContentTypes;
import com.mongodb.stitch.core.internal.net.Headers;
import com.mongodb.stitch.core.internal.net.Method;
import com.mongodb.stitch.core.internal.net.StitchAppRoutes;
import com.mongodb.stitch.core.internal.net.StitchAuthDocRequest;
import com.mongodb.stitch.core.internal.net.StitchAuthRequest;
import com.mongodb.stitch.core.internal.net.StitchDocRequest;
import com.mongodb.stitch.core.internal.net.StitchRequest;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;
import com.mongodb.stitch.core.mock.MockCoreAnonymousAuthProviderClient;
import com.mongodb.stitch.core.auth.providers.userpass.CoreUserPasswordAuthProviderClient;
import com.mongodb.stitch.core.auth.providers.userpass.UserPasswordCredential;
import com.mongodb.stitch.core.internal.common.StitchObjectMapper;
import com.mongodb.stitch.core.internal.net.Response;
import com.mongodb.stitch.core.mock.MockCoreStitchAuthForCodecTests;
import com.mongodb.stitch.core.mock.MockRequestClientForCodecTests;
import com.mongodb.stitch.core.testutil.Constants;
import com.mongodb.stitch.core.testutil.CustomType;

import org.bson.Document;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.IntegerCodec;
import org.bson.codecs.StringCodec;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreStitchAuthUnitTests {
    private static final class MockRequestClient extends StitchRequestClient {
        static final StitchAppRoutes APP_ROUTES = new StitchAppRoutes("<app-id>");
        static final String USER_ID = new ObjectId().toHexString();

        static final Map<String, Object> MOCK_API_PROFILE;
        static {
            final HashMap<String, Object> map = new HashMap<>();

            map.put("type", "foo");

            List<Map<String, String>> identities = new ArrayList<>();
            Map<String, String> identity1 = new HashMap<>();
            identity1.put("id", "bar");
            identity1.put("provider_type", "baz");
            identities.add(identity1);
            map.put("identities", identities);

            map.put("data", new HashMap<>());
            MOCK_API_PROFILE = map;
        }

        static final APIAuthInfo MOCK_API_AUTH_INFO;
        static {
            final ObjectMapper mapper = StitchObjectMapper.getInstance();

            final Map<String, String> apiAuthInfoMap = new HashMap<>();

            apiAuthInfoMap.put("user_id", USER_ID);
            apiAuthInfoMap.put("device_id", new ObjectId().toHexString());

            final String jwt = Jwts.builder()
                    .setIssuedAt(new Date())
                    .setSubject("uniqueUserID")
                    .setExpiration(new Date(((Calendar.getInstance().getTimeInMillis() + (5*60*1000)))))
                    .signWith(SignatureAlgorithm.HS256,
                            "abcdefghijklmnopqrstuvwxyz1234567890".getBytes())
                    .compact();

            apiAuthInfoMap.put("access_token", jwt);
            apiAuthInfoMap.put("refresh_token", jwt);

            APIAuthInfo apiAuthInfo = null;
            try {
                apiAuthInfo =
                        mapper.readValue(mapper.writeValueAsString(apiAuthInfoMap), APIAuthInfo.class);
            } catch (Exception e) {
                e.printStackTrace();
            }

            MOCK_API_AUTH_INFO = apiAuthInfo;
        }

        static final Map<String, String> MOCK_SESSION_INFO;
        static {
            final Map<String, String> mockSessionInfo = new HashMap<>();

            mockSessionInfo.put("access_token", Jwts.builder()
                    .setIssuedAt(new Date())
                    .setSubject("uniqueUserID")
                    .setExpiration(new Date(((Calendar.getInstance().getTimeInMillis() + (10*60*1000)))))
                    .signWith(SignatureAlgorithm.HS256,
                            "abcdefghijklmnopqrstuvwxyz1234567890".getBytes())
                    .compact());

            MOCK_SESSION_INFO = mockSessionInfo;
        }

        static final Map<String, String> BASE_JSON_HEADERS;
        static {
            final HashMap<String, String> map = new HashMap<>();
            map.put(Headers.CONTENT_TYPE, ContentTypes.APPLICATION_JSON);
            BASE_JSON_HEADERS = map;
        }

        private Function<StitchRequest, Response> handleAuthProviderLoginRoute =
                (StitchRequest request) -> {
            try {
                return new Response(
                        200,
                        BASE_JSON_HEADERS,
                        new ByteArrayInputStream(
                                StitchObjectMapper.getInstance().writeValueAsBytes(
                                        MOCK_API_AUTH_INFO
                                )
                        )
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        };

        private Function<StitchRequest, Response> handleAuthProviderLinkRoute =
                (StitchRequest request) -> {
            try {
                return new Response(
                        200,
                        BASE_JSON_HEADERS,
                        new ByteArrayInputStream(
                                StitchObjectMapper.getInstance().writeValueAsBytes(
                                        MOCK_API_AUTH_INFO
                                )
                        )
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        };

        private Function<StitchRequest, Response> handleProfileRoute =
                (StitchRequest request) -> {
            try {
                return new Response(
                        200,
                        BASE_JSON_HEADERS,
                        new ByteArrayInputStream(
                                StitchObjectMapper.getInstance().writeValueAsBytes(
                                        MOCK_API_PROFILE
                                )
                        )
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        };

        private Function<StitchRequest, Response> handleSessionRoute = (StitchRequest request) -> {
            try {
                return new Response(
                        200,
                        BASE_JSON_HEADERS,
                        new ByteArrayInputStream(
                                StitchObjectMapper.getInstance().writeValueAsBytes(
                                        MOCK_SESSION_INFO
                                )
                        )
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        };

        MockRequestClient() {
            super("http://localhost:8080",
                    null,
                    Constants.DEFAULT_TRANSPORT_TIMEOUT_MILLISECONDS);
        }

        Function<StitchRequest, Response> getHandleAuthProviderLinkRoute() {
            return handleAuthProviderLinkRoute;
        }

        void setHandleAuthProviderLinkRoute(Function<StitchRequest, Response> handleAuthProviderLinkRoute) {
            this.handleAuthProviderLinkRoute = handleAuthProviderLinkRoute;
        }

        private Response handleRequest(StitchRequest request) {
            if (request.path.equals(
                    APP_ROUTES.getAuthRoutes().getAuthProviderLoginRoute("anon-user")
            )) {
                return this.handleAuthProviderLoginRoute.apply(request);
            } else if (request.path.equals(
                    APP_ROUTES.getAuthRoutes().getProfileRoute()
            )) {
                return this.handleProfileRoute.apply(request);
            } else if (request.path.equals(
                    APP_ROUTES.getAuthRoutes().getAuthProviderLinkRoute("local-userpass")
            )) {
                return this.handleAuthProviderLinkRoute.apply(request);
            } else if (request.path.equals(
                    APP_ROUTES.getAuthRoutes().getSessionRoute()
            )) {
                return this.handleSessionRoute.apply(request);
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

    public final class MockCoreStitchAuth extends CoreStitchAuth<CoreStitchUserImpl> {
        int authenticatedRequestFired = 0;

        @Override
        protected Document getDeviceInfo() {
            return null;
        }

        @Override
        protected StitchUserFactory<CoreStitchUserImpl> getUserFactory() {
            return (String id,
                    String loggedInProviderType,
                    String loggedInProviderName,
                    StitchUserProfileImpl userProfile) ->
                    new CoreStitchUserImpl(
                            id,
                            loggedInProviderType,
                            loggedInProviderName,
                            userProfile
                    ) { };
        }

        @Override
        protected void onAuthEvent() {

        }

        MockCoreStitchAuth(MockRequestClient mockRequestClient) {
            super(
                    mockRequestClient,
                    MockRequestClient.APP_ROUTES.getAuthRoutes(),
                    new MemoryStorage(),
                    null
            );
        }

        @Override
        public Response doAuthenticatedRequest(StitchAuthRequest stitchReq) {
            authenticatedRequestFired++;
            return super.doAuthenticatedRequest(stitchReq);
        }
    }

    @Test
    void testLoginWithCredentialBlocking() {
        final MockCoreStitchAuth coreStitchAuth = new MockCoreStitchAuth(new MockRequestClient());

        final CoreStitchUser user =
                coreStitchAuth.loginWithCredentialBlocking(
                        new MockCoreAnonymousAuthProviderClient().getCredential()
                );

        assertEquals(user.getId(), MockRequestClient.USER_ID);
        assertEquals(user.getLoggedInProviderName(), "anon-user");
        assertEquals(user.getLoggedInProviderType(), "anon-user");
        assertEquals(user.getUserType(), "foo");
        assertEquals(user.getIdentities().get(0).getId(), "bar");
    }

    @Test
    void testLinkUserWithCredentialBlocking() {
        final MockCoreStitchAuth coreStitchAuth = new MockCoreStitchAuth(new MockRequestClient());

        final CoreStitchUser user =
                coreStitchAuth.loginWithCredentialBlocking(
                        new MockCoreAnonymousAuthProviderClient().getCredential()
                );

        final CoreStitchUser linkedUser = coreStitchAuth.linkUserWithCredentialBlocking(
                user,
                new UserPasswordCredential(
                        CoreUserPasswordAuthProviderClient.DEFAULT_PROVIDER_NAME,
                        "foo@foo.com",
                        "bar"
                )
        );

        assertEquals(linkedUser.getId(), user.getId());
    }

    @Test
    void testIsLoggedIn() {
        final MockCoreStitchAuth coreStitchAuth = new MockCoreStitchAuth(new MockRequestClient());

        coreStitchAuth.loginWithCredentialBlocking(
                new MockCoreAnonymousAuthProviderClient().getCredential()
        );

        assertTrue(coreStitchAuth.isLoggedIn());
    }

    @Test
    void testLogoutBlocking() {
        final MockCoreStitchAuth coreStitchAuth = new MockCoreStitchAuth(new MockRequestClient());

        assertFalse(coreStitchAuth.isLoggedIn());

        coreStitchAuth.loginWithCredentialBlocking(
                new MockCoreAnonymousAuthProviderClient().getCredential()
        );

        assertTrue(coreStitchAuth.isLoggedIn());

        coreStitchAuth.logoutBlocking();

        assertFalse(coreStitchAuth.isLoggedIn());
    }

    @Test
    void testHasDeviceId() {
        final MockCoreStitchAuth coreStitchAuth = new MockCoreStitchAuth(new MockRequestClient());

        assertFalse(coreStitchAuth.hasDeviceId());

        coreStitchAuth.loginWithCredentialBlocking(
                new MockCoreAnonymousAuthProviderClient().getCredential()
        );

        assertTrue(coreStitchAuth.hasDeviceId());
    }

    @Test
    void testHandleAuthFailure() {
        final MockRequestClient mockRequestClient = new MockRequestClient();
        final MockCoreStitchAuth coreStitchAuth = new MockCoreStitchAuth(mockRequestClient);

        final Function<StitchRequest, Response> oldLinkFunc =
                mockRequestClient.getHandleAuthProviderLinkRoute();
        mockRequestClient.setHandleAuthProviderLinkRoute((StitchRequest request) -> {
            mockRequestClient.setHandleAuthProviderLinkRoute(oldLinkFunc);

            throw new StitchServiceException("InvalidSession", StitchServiceErrorCode.INVALID_SESSION);
        });

        final CoreStitchUser user =
                coreStitchAuth.loginWithCredentialBlocking(
                        new MockCoreAnonymousAuthProviderClient().getCredential()
                );

        coreStitchAuth.linkUserWithCredentialBlocking(
                user,
                new UserPasswordCredential(
                        CoreUserPasswordAuthProviderClient.DEFAULT_PROVIDER_NAME,
                        "foo@foo.com",
                        "bar"
                )
        );

        assertEquals(4, coreStitchAuth.authenticatedRequestFired);
    }

    @Test
    void testDoAuthenticatedJSONRequestWithDefaultCodecRegistry() {
        final MockRequestClientForCodecTests mockRequestClient = new MockRequestClientForCodecTests();
        final MockCoreStitchAuthForCodecTests coreStitchAuth = new MockCoreStitchAuthForCodecTests(mockRequestClient);

        final StitchAuthDocRequest.Builder reqBuilder = new StitchAuthDocRequest.Builder();
        reqBuilder.withDocument(new Document());
        reqBuilder.withMethod(Method.POST);

        // Check that primitive return types can be decoded.
        reqBuilder.withPath(MockRequestClientForCodecTests.PRIMITIVE_VALUE_TEST_ROUTE);
        assertEquals(42, (int) coreStitchAuth.doAuthenticatedJSONRequest(reqBuilder.build(), Integer.class));
        assertEquals(42, (int) coreStitchAuth.doAuthenticatedJSONRequest(reqBuilder.build(), new IntegerCodec()));

        // Check that the proper exceptions are thrown when decoding into the incorrect type.
        assertThrows(
                StitchRequestException.class,
                () -> coreStitchAuth.doAuthenticatedJSONRequest(reqBuilder.build(), String.class)
        );
        assertThrows(
                StitchRequestException.class,
                () -> coreStitchAuth.doAuthenticatedJSONRequest(reqBuilder.build(), new StringCodec())
        );

        // Check that BSON documents returned as extended JSON can be decoded.
        reqBuilder.withPath(MockRequestClientForCodecTests.DOCUMENT_VALUE_TEST_ROUTE);
        final ObjectId expectedObjectId = new ObjectId(MockRequestClientForCodecTests.DOCUMENT_ID);

        Document doc = coreStitchAuth.doAuthenticatedJSONRequest(reqBuilder.build(), Document.class);
        assertEquals(expectedObjectId, doc.getObjectId("_id"));
        assertEquals(42, (int) doc.getInteger("intValue"));

        doc = coreStitchAuth.doAuthenticatedJSONRequest(reqBuilder.build(), new DocumentCodec());
        assertEquals(expectedObjectId, doc.getObjectId("_id"));
        assertEquals(42, (int) doc.getInteger("intValue"));

        // Check that BSON documents returned as extended JSON can be decoded as a custom type if
        // the codec is specifically provided.
        final CustomType ct = coreStitchAuth.doAuthenticatedJSONRequest(reqBuilder.build(), new CustomType.Codec());
        assertEquals(expectedObjectId, ct.getId());
        assertEquals(42, ct.getIntValue());

        // Check that the correct exception is thrown if attempting to decode as a particular class
        // type if the coreStitchAuth was never configured to contain the provided class type
        // codec.
        assertThrows(
                StitchRequestException.class,
                () -> coreStitchAuth.doAuthenticatedJSONRequest(reqBuilder.build(), CustomType.class)
        );

        // Check that BSON arrays can be decoded
        reqBuilder.withPath(MockRequestClientForCodecTests.LIST_VALUE_TEST_ROUTE);
        List list = coreStitchAuth.doAuthenticatedJSONRequest(reqBuilder.build(), List.class);
        assertEquals(Arrays.asList(21, "the meaning of life, the universe, and everything", 84, 168), list);
    }

    @Test
    void testDoAuthenticatedJSONRequestWithCustomCodecRegistry() {
        final MockRequestClientForCodecTests mockRequestClient = new MockRequestClientForCodecTests();
        final MockCoreStitchAuthForCodecTests coreStitchAuth = new MockCoreStitchAuthForCodecTests(
                mockRequestClient,
                CodecRegistries.fromRegistries(
                        BSONUtils.DEFAULT_CODEC_REGISTRY,
                        CodecRegistries.fromCodecs(new CustomType.Codec())
                )
        );

        final StitchAuthDocRequest.Builder reqBuilder = new StitchAuthDocRequest.Builder();
        reqBuilder.withDocument(new Document());
        reqBuilder.withMethod(Method.POST);

        // Check that primitive return types can still be decoded.
        reqBuilder.withPath(MockRequestClientForCodecTests.PRIMITIVE_VALUE_TEST_ROUTE);
        assertEquals(42, (int) coreStitchAuth.doAuthenticatedJSONRequest(reqBuilder.build(), Integer.class));
        assertEquals(42, (int) coreStitchAuth.doAuthenticatedJSONRequest(reqBuilder.build(), new IntegerCodec()));

        reqBuilder.withPath(MockRequestClientForCodecTests.DOCUMENT_VALUE_TEST_ROUTE);
        final ObjectId expectedObjectId = new ObjectId(MockRequestClientForCodecTests.DOCUMENT_ID);

        // Check that BSON documents returned as extended JSON can still be decoded into BSON
        // documents.
        Document doc = coreStitchAuth.doAuthenticatedJSONRequest(reqBuilder.build(), Document.class);
        assertEquals(expectedObjectId, doc.getObjectId("_id"));
        assertEquals(42, (int) doc.getInteger("intValue"));

        doc = coreStitchAuth.doAuthenticatedJSONRequest(reqBuilder.build(), new DocumentCodec());
        assertEquals(expectedObjectId, doc.getObjectId("_id"));
        assertEquals(42, (int) doc.getInteger("intValue"));

        // Check that a custom type can be decoded without providing a codec, as long as that codec
        // is registered in the CoreStitchAuth's configuration.
        final CustomType ct = coreStitchAuth.doAuthenticatedJSONRequest(reqBuilder.build(), CustomType.class);
        assertEquals(expectedObjectId, ct.getId());
        assertEquals(42, ct.getIntValue());
    }
}
