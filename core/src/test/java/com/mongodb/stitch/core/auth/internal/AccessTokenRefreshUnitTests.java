package com.mongodb.stitch.core.auth.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.stitch.core.auth.StitchCredential;
import com.mongodb.stitch.core.auth.internal.models.APIAuthInfo;
import com.mongodb.stitch.core.internal.common.MemoryStorage;
import com.mongodb.stitch.core.internal.common.StitchObjectMapper;
import com.mongodb.stitch.core.internal.net.ContentTypes;
import com.mongodb.stitch.core.internal.net.Headers;
import com.mongodb.stitch.core.internal.net.Response;
import com.mongodb.stitch.core.internal.net.StitchAppRoutes;
import com.mongodb.stitch.core.internal.net.StitchAuthRequest;
import com.mongodb.stitch.core.internal.net.StitchDocRequest;
import com.mongodb.stitch.core.internal.net.StitchRequest;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;
import com.mongodb.stitch.core.mock.MockCoreAnonymousAuthProviderClient;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import static org.junit.jupiter.api.Assertions.assertEquals;


class AccessTokenRefreshUnitTests {
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
                Assertions.fail(e);
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
                    .setExpiration(new Date(((Calendar.getInstance().getTimeInMillis() + (5*60*1000)))))
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
                        Assertions.fail(e);
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
                        Assertions.fail(e);
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
                        Assertions.fail(e);
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
                Assertions.fail(e);
                return null;
            }
        };

        MockRequestClient() {
            super("http://localhost:8080", null);
        }

        void setHandleAuthProviderLoginRoute(Function<StitchRequest, Response> handleAuthProviderLoginRoute) {
            this.handleAuthProviderLoginRoute = handleAuthProviderLoginRoute;
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
        int loginRequestFired = 0;

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
        public synchronized CoreStitchUserImpl loginWithCredentialBlocking(StitchCredential credential) {
            loginRequestFired++;
            return super.loginWithCredentialBlocking(credential);
        }

        @Override
        public Response doAuthenticatedRequest(StitchAuthRequest stitchReq) {
            authenticatedRequestFired++;
            return super.doAuthenticatedRequest(stitchReq);
        }
    }

    private final static String EXPIRED_JWT =  Jwts.builder()
            .setIssuedAt(new Date(((Calendar.getInstance().getTimeInMillis() - (10*60*1000)))))
            .setSubject("uniqueUserID")
            .setExpiration(new Date(((Calendar.getInstance().getTimeInMillis() - (5*60*1000)))))
            .signWith(SignatureAlgorithm.HS256,
                    "abcdefghijklmnopqrstuvwxyz1234567890".getBytes()).compact();

    private final static String GOOD_JWT =  Jwts.builder()
            .setIssuedAt(new Date())
            .setSubject("uniqueUserID")
            .setExpiration(new Date(((Calendar.getInstance().getTimeInMillis() + (20*60*1000)))))
            .signWith(SignatureAlgorithm.HS256,
                    "abcdefghijklmnopqrstuvwxyz1234567890".getBytes()).compact();


    private static final Document MOCK_GOOD_AUTH_INFO;
    static {
        Document document = new Document();

        document.put("access_token", GOOD_JWT);
        document.put("refresh_token", GOOD_JWT);
        document.put("user_id", new ObjectId().toHexString());
        document.put("device_id", new ObjectId().toHexString());

        MOCK_GOOD_AUTH_INFO = document;
    }

    private static final Document MOCK_EXPIRED_AUTH_INFO;
    static {
        Document document = new Document();

        document.put("access_token", EXPIRED_JWT);
        document.put("refresh_token", EXPIRED_JWT);
        document.put("user_id", new ObjectId().toHexString());
        document.put("device_id", new ObjectId().toHexString());

        MOCK_EXPIRED_AUTH_INFO = document;
    }

    @Test
    void testCheckRefresh() throws Exception {
        MockRequestClient mockRequestClient = new MockRequestClient();

        // swap out login route good data for expired data
        mockRequestClient.setHandleAuthProviderLoginRoute((StitchRequest request) -> {
            try {
                return new Response(
                        200,
                        MockRequestClient.BASE_JSON_HEADERS,
                        new ByteArrayInputStream(
                                StitchObjectMapper.getInstance().writeValueAsBytes(
                                        MOCK_GOOD_AUTH_INFO
                                )
                        )
                );
            } catch (Exception e) {
                e.printStackTrace();
                Assertions.fail(e);
                return null;
            }
        });

        MockCoreStitchAuth mockCoreAuth = new MockCoreStitchAuth(
                mockRequestClient
        );

        mockCoreAuth.loginWithCredentialBlocking(
                new MockCoreAnonymousAuthProviderClient().getCredential()
        );

        AccessTokenRefresher<CoreStitchUserImpl> accessTokenRefresher = new AccessTokenRefresher<>(
                new WeakReference<>(mockCoreAuth)
        );

        assertEquals(1, mockCoreAuth.authenticatedRequestFired);

        accessTokenRefresher.checkRefresh();

        // setter should only have been accessed once for login
        assertEquals(1, mockCoreAuth.authenticatedRequestFired);

        // swap out login route good data for expired data
        mockRequestClient.setHandleAuthProviderLoginRoute((StitchRequest request) -> {
            try {
                return new Response(
                        200,
                        MockRequestClient.BASE_JSON_HEADERS,
                        new ByteArrayInputStream(
                                StitchObjectMapper.getInstance().writeValueAsBytes(
                                        MOCK_EXPIRED_AUTH_INFO
                                )
                        )
                );
            } catch (Exception e) {
                e.printStackTrace();
                Assertions.fail(e);
                return null;
            }
        });

        // logout and relogin. setterAccessor should be accessed twice after this
        mockCoreAuth.logoutBlocking();

        mockCoreAuth.loginWithCredentialBlocking(
                new MockCoreAnonymousAuthProviderClient().getCredential()
        );

        assertEquals(3, mockCoreAuth.authenticatedRequestFired);

        // check refresh, which SHOULD trigger a refresh, and the setter
        accessTokenRefresher.checkRefresh();

        assertEquals(4, mockCoreAuth.authenticatedRequestFired);
    }
}
