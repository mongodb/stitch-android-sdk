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

package com.mongodb.stitch.core.auth.providers.userpass;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.mongodb.stitch.core.internal.common.StitchObjectMapper;
import com.mongodb.stitch.core.internal.net.ContentTypes;
import com.mongodb.stitch.core.internal.net.Headers;
import com.mongodb.stitch.core.internal.net.Request;
import com.mongodb.stitch.core.internal.net.Response;
import com.mongodb.stitch.core.internal.net.StitchAppRoutes;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import org.bson.Document;
import org.junit.Test;

public class CoreUserPasswordAuthProviderClientUnitTests {
  private static final String baseURL = "";
  private static final StitchAppRoutes routes = new StitchAppRoutes("<app-id>");

  private static final String providerName = "local-userpass";
  private static final Map<String, String> BASE_JSON_HEADERS = new HashMap<>();

  static {
    BASE_JSON_HEADERS.put(Headers.CONTENT_TYPE, ContentTypes.APPLICATION_JSON);
  }

  private static final String username = "username@10gen.com";
  private static final String password = "password";
  private static final CoreUserPasswordAuthProviderClient core =
      new CoreUserPasswordAuthProviderClient(
          providerName,
          new StitchRequestClient(
              baseURL,
              (Request request) -> {
                try {
                  final Document body =
                      StitchObjectMapper.getInstance().readValue(request.getBody(), Document.class);

                  if (request.getUrl().contains("register")) {
                    assertEquals(
                        new Document("email", username).append("password", password),
                        body);
                  } else if (request.getUrl().endsWith("confirm")) {
                    assertEquals(new Document("tokenId", "tokenId").append("token", "token"), body);
                  } else if (request.getUrl().endsWith("send")) {
                    assertEquals(new Document("email", username), body);
                  } else {
                    return null;
                  }

                  return new Response(
                      200,
                      BASE_JSON_HEADERS,
                      new ByteArrayInputStream(
                          StitchObjectMapper.getInstance().writeValueAsBytes(new Document())));
                } catch (final Exception e) {
                  fail(e.getMessage());
                  return null;
                }
              }),
          routes.getAuthRoutes()) {};

  @Test
  public void testCredential() {
    final UserPasswordCredential credential = core.getCredential(username, password);

    assertEquals(providerName, credential.getProviderName());

    assertEquals(username, credential.getMaterial().get("username"));
    assertEquals(password, credential.getMaterial().get("password"));
    assertEquals(false, credential.getProviderCapabilities().getReusesExistingSession());
  }

  @Test
  public void testRegister() {
    core.registerWithEmailInternal(username, password);
  }

  @Test
  public void testConfirmUser() {
    core.confirmUserInternal("token", "tokenId");
  }

  @Test
  public void testResendConfirmation() {
    core.resendConfirmationEmailInternal(username);
  }
}
