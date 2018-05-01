package com.mongodb.stitch.core.auth.providers.userpass;

import com.mongodb.stitch.core.internal.common.StitchObjectMapper;
import com.mongodb.stitch.core.internal.net.ContentTypes;
import com.mongodb.stitch.core.internal.net.Headers;
import com.mongodb.stitch.core.internal.net.Request;
import com.mongodb.stitch.core.internal.net.Response;
import com.mongodb.stitch.core.internal.net.StitchAppRoutes;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;

import org.bson.Document;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class CoreUserPasswordAuthProviderClientUnitTests {
    private static final String baseURL = "";
    private static final StitchAppRoutes routes = new StitchAppRoutes("<app-id>");

    private static final String providerName = "local-userpass";
    private static final Map<String, String> BASE_JSON_HEADERS = new HashMap<>();
    static {
        BASE_JSON_HEADERS.put(Headers.CONTENT_TYPE, ContentTypes.APPLICATION_JSON);
    }

    private final CoreUserPasswordAuthProviderClient core = new CoreUserPasswordAuthProviderClient(
            providerName,
            new StitchRequestClient(baseURL, (Request request) -> {
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
                } catch (final Exception e) {
                    fail(e.getMessage());
                    return null;
                }
            }),
            routes.getAuthRoutes()
    ) { };


    private final String username = "username@10gen.com";
    private final String password = "password";

    @Test
    public void testCredential() {
        final UserPasswordCredential credential = core.getCredential(
                this.username,
                this.password
        );

        assertEquals(providerName, credential.getProviderName());

        assertEquals(this.username, credential.getMaterial().get("username"));
        assertEquals(this.password, credential.getMaterial().get("password"));
        assertEquals(false, credential.getProviderCapabilities().reusesExistingSession);
    }

    @Test
    public void testRegister() {
        core.registerWithEmailInternal(
                this.username,
                this.password
        );
    }

    @Test
    public void testConfirmUser() {
        core.confirmUserInternal("token", "tokenId");
    }

    @Test
    public void testResendConfirmation() {
        core.resendConfirmationEmailInternal(this.username);
    }
}
