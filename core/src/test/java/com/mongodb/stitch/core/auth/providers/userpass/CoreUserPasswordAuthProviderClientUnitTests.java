package com.mongodb.stitch.core.auth.providers.userpass;

import com.mongodb.stitch.core.internal.common.StitchObjectMapper;
import com.mongodb.stitch.core.internal.net.ContentTypes;
import com.mongodb.stitch.core.internal.net.Headers;
import com.mongodb.stitch.core.internal.net.Request;
import com.mongodb.stitch.core.internal.net.Response;
import com.mongodb.stitch.core.internal.net.StitchAppRoutes;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;
import com.mongodb.stitch.core.testutil.Constants;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CoreUserPasswordAuthProviderClientUnitTests {
    private final String baseURL = "";
    private final StitchAppRoutes routes = new StitchAppRoutes("<app-id>");

    private final String providerName = "local-userpass";
    private static final Map<String, String> BASE_JSON_HEADERS;
    static {
        final HashMap<String, String> map = new HashMap<>();
        map.put(Headers.CONTENT_TYPE, ContentTypes.APPLICATION_JSON);
        BASE_JSON_HEADERS = map;
    }

    private final CoreUserPasswordAuthProviderClient core = new CoreUserPasswordAuthProviderClient(
            this.providerName,
            new StitchRequestClient(this.baseURL, (Request request) -> {
                try {
                    final Document body = StitchObjectMapper.getInstance().readValue(
                            request.body,
                            Document.class
                    );

                    if (request.url.contains("register")) {
                        assertEquals(new Document("email", this.username)
                                .append("password", this.password), body);
                    } else if (request.url.endsWith("confirm")) {
                        assertEquals(new Document("tokenId", "tokenId")
                                .append("token", "token"), body);
                    } else if (request.url.endsWith("send")) {
                        assertEquals(new Document("email", this.username), body);
                    } else {
                        return null;
                    }

                    return new Response(
                            200,
                            BASE_JSON_HEADERS,
                            new ByteArrayInputStream(
                                    StitchObjectMapper.getInstance().writeValueAsBytes(
                                            new Document()
                                    )
                            )
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            },
            Constants.DEFAULT_TRANSPORT_TIMEOUT_MILLISECONDS),
            this.routes.getAuthRoutes()
    ) { };


    private final String username = "username@10gen.com";
    private final String password = "password";

    @Test
    void testCredential() {
        final UserPasswordCredential credential = core.getCredential(
                this.username,
                this.password
        );

        assertEquals(this.providerName, credential.getProviderName());

        assertEquals(this.username, credential.getMaterial().get("username"));
        assertEquals(this.password, credential.getMaterial().get("password"));
        assertEquals(false, credential.getProviderCapabilities().reusesExistingSession);
    }

    @Test
    void testRegister() {
        assertAll(() -> core.registerWithEmailInternal(
                this.username,
                this.password
        ));
    }

    @Test
    void testConfirmUser() {
        assertAll(() -> core.confirmUserInternal("token", "tokenId"));
    }

    @Test
    void testResendConfirmation() {
        assertAll(() -> core.resendConfirmationEmailInternal(this.username));
    }
}
