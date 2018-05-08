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

package com.mongodb.stitch.core.auth.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mongodb.stitch.core.StitchRequestException;
import com.mongodb.stitch.core.StitchServiceErrorCode;
import com.mongodb.stitch.core.StitchServiceException;
import com.mongodb.stitch.core.auth.providers.anonymous.CoreAnonymousAuthProviderClient;
import com.mongodb.stitch.core.auth.providers.userpass.CoreUserPasswordAuthProviderClient;
import com.mongodb.stitch.core.auth.providers.userpass.UserPasswordCredential;
import com.mongodb.stitch.core.internal.common.BsonUtils;
import com.mongodb.stitch.core.internal.common.MemoryStorage;
import com.mongodb.stitch.core.internal.common.StitchObjectMapper;
import com.mongodb.stitch.core.internal.common.Storage;
import com.mongodb.stitch.core.internal.net.Method;
import com.mongodb.stitch.core.internal.net.Response;
import com.mongodb.stitch.core.internal.net.StitchAuthDocRequest;
import com.mongodb.stitch.core.internal.net.StitchRequest;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;
import com.mongodb.stitch.core.testutil.CustomType;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.IntegerCodec;
import org.bson.codecs.StringCodec;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.mockito.Mockito;

public class CoreStitchAuthUnitTests {

  static String DEFAULT_ACCESS_TOKEN = getTestAccessToken();
  static String DEFAULT_REFRESH_TOKEN = getTestRefreshToken();

  static String getTestAccessToken() {
    return Jwts.builder()
        .setIssuedAt(Date.from(Instant.now().minus(Duration.ofHours(1))))
        .setSubject("uniqueUserID")
        .setExpiration(new Date(((Calendar.getInstance().getTimeInMillis() + (5 * 60 * 1000)))))
        .signWith(SignatureAlgorithm.HS256, "abcdefghijklmnopqrstuvwxyz1234567890".getBytes())
        .compact();
  }

  // TODO: Add differentiating info
  static String getTestRefreshToken() {
    return Jwts.builder()
        .setIssuedAt(Date.from(Instant.now().minus(Duration.ofHours(1))))
        .setSubject("uniqueUserID")
        .setExpiration(new Date(((Calendar.getInstance().getTimeInMillis() + (5 * 60 * 1000)))))
        .signWith(SignatureAlgorithm.HS256, "abcdefghijklmnopqrstuvwxyz1234567890".getBytes())
        .compact();
  }

  @Test
  public void testLoginWithCredentialBlocking() {
    final StitchRequestClient requestClient = getMockedRequestClient();
    final StitchAuth auth =
        new StitchAuth(requestClient, new TestAuthRoutes(), new MemoryStorage());

    final CoreStitchUser user =
        auth.loginWithCredentialBlocking(new CoreAnonymousAuthProviderClient().getCredential());

    // TODO: use test constants
    assertEquals(user.getId(), "some-unique-user-id");
    assertEquals(user.getLoggedInProviderName(), "anon-user");
    assertEquals(user.getLoggedInProviderType(), "anon-user");
    assertEquals(user.getUserType(), "foo");
    assertEquals(user.getIdentities().get(0).getId(), "bar");

    assertEquals(user, auth.getUser());
    assertTrue(auth.isLoggedIn());

    // TODO: test failure cases
  }

  @Test
  public void testLinkUserWithCredentialBlocking() {
    final StitchRequestClient requestClient = getMockedRequestClient();
    final StitchAuth auth =
        new StitchAuth(requestClient, new TestAuthRoutes(), new MemoryStorage());

    final CoreStitchUser user =
        auth.loginWithCredentialBlocking(new CoreAnonymousAuthProviderClient().getCredential());

    final CoreStitchUser linkedUser =
        auth.linkUserWithCredentialBlocking(
            user,
            new UserPasswordCredential(
                CoreUserPasswordAuthProviderClient.DEFAULT_PROVIDER_NAME, "foo@foo.com", "bar"));

    assertEquals(linkedUser.getId(), user.getId());

    // TODO: test failure cases
  }

  @Test
  public void testIsLoggedIn() {
    final StitchRequestClient requestClient = getMockedRequestClient();
    final StitchAuth auth =
        new StitchAuth(requestClient, new TestAuthRoutes(), new MemoryStorage());

    auth.loginWithCredentialBlocking(new CoreAnonymousAuthProviderClient().getCredential());

    assertTrue(auth.isLoggedIn());
  }

  @Test
  public void testLogoutBlocking() {
    final StitchRequestClient requestClient = getMockedRequestClient();
    final StitchAuth auth =
        new StitchAuth(requestClient, new TestAuthRoutes(), new MemoryStorage());

    assertFalse(auth.isLoggedIn());

    auth.loginWithCredentialBlocking(new CoreAnonymousAuthProviderClient().getCredential());

    assertTrue(auth.isLoggedIn());

    auth.logoutBlocking();

    assertFalse(auth.isLoggedIn());
  }

  @Test
  public void testHasDeviceId() {
    final StitchRequestClient requestClient = getMockedRequestClient();
    final StitchAuth auth =
        new StitchAuth(requestClient, new TestAuthRoutes(), new MemoryStorage());

    assertFalse(auth.hasDeviceId());

    auth.loginWithCredentialBlocking(new CoreAnonymousAuthProviderClient().getCredential());

    assertTrue(auth.hasDeviceId());
  }

  @Test
  public void testHandleAuthFailure() {
    final StitchRequestClient requestClient = getMockedRequestClient();
    final StitchAuth auth =
        Mockito.spy(new StitchAuth(requestClient, new TestAuthRoutes(), new MemoryStorage()));

    CoreStitchUser user =
        auth.loginWithCredentialBlocking(new CoreAnonymousAuthProviderClient().getCredential());

    doNothing().when(auth).refreshAccessToken();

    doThrow(new StitchServiceException(StitchServiceErrorCode.INVALID_SESSION))
        .doReturn(
            new Response(getMockedAuthInfoResponse(DEFAULT_ACCESS_TOKEN, DEFAULT_REFRESH_TOKEN)))
        .when(requestClient)
        .doRequest(argThat(req -> req.getPath().endsWith("/link")));

    user =
        auth.linkUserWithCredentialBlocking(
            user,
            new UserPasswordCredential(
                CoreUserPasswordAuthProviderClient.DEFAULT_PROVIDER_NAME, "foo@foo.com", "bar"));

    assertTrue(auth.isLoggedIn());
    verify(auth, times(1)).refreshAccessToken();

    // This should log the user out
    doThrow(new StitchServiceException(StitchServiceErrorCode.INVALID_SESSION))
        .when(requestClient)
        .doRequest(argThat(req -> req.getPath().endsWith("/link")));

    try {
      auth.linkUserWithCredentialBlocking(
          user,
          new UserPasswordCredential(
              CoreUserPasswordAuthProviderClient.DEFAULT_PROVIDER_NAME, "foo@foo.com", "bar"));
      fail();
    } catch (final StitchServiceException ex) {
      assertEquals(ex.getErrorCode(), StitchServiceErrorCode.INVALID_SESSION);
    }

    assertFalse(auth.isLoggedIn());
    verify(auth, times(2)).refreshAccessToken();
  }

  @Test
  public void testDoAuthenticatedJsonRequestWithDefaultCodecRegistry() {
    final StitchRequestClient requestClient = getMockedRequestClient();
    final StitchAuth auth =
        new StitchAuth(requestClient, new TestAuthRoutes(), new MemoryStorage());
    auth.loginWithCredentialBlocking(new CoreAnonymousAuthProviderClient().getCredential());

    final StitchAuthDocRequest.Builder reqBuilder = new StitchAuthDocRequest.Builder();
    reqBuilder.withPath("giveMeData");
    reqBuilder.withDocument(new Document());
    reqBuilder.withMethod(Method.POST);

    final String rawInt = "{\"$numberInt\": \"42\"}";
    // Check that primitive return types can be decoded.
    doReturn(new Response(rawInt)).when(requestClient).doRequest(any());
    assertEquals(42, (int) auth.doAuthenticatedJsonRequest(reqBuilder.build(), Integer.class));
    doReturn(new Response(rawInt)).when(requestClient).doRequest(any());
    assertEquals(42, (int) auth.doAuthenticatedJsonRequest(reqBuilder.build(), new IntegerCodec()));

    // Check that the proper exceptions are thrown when decoding into the incorrect type.
    doReturn(new Response(rawInt)).when(requestClient).doRequest(any());
    try {
      auth.doAuthenticatedJsonRequest(reqBuilder.build(), String.class);
      fail();
    } catch (final StitchRequestException ignored) {
      // do nothing
    }

    doReturn(new Response(rawInt)).when(requestClient).doRequest(any());
    try {
      auth.doAuthenticatedJsonRequest(reqBuilder.build(), new StringCodec());
      fail();
    } catch (final StitchRequestException ignored) {
      // do nothing
    }

    // Check that BSON documents returned as extended JSON can be decoded.
    final ObjectId expectedObjectId = new ObjectId();
    final String docRaw =
        String.format(
            "{\"_id\": {\"$oid\": \"%s\"}, \"intValue\": {\"$numberInt\": \"42\"}}",
            expectedObjectId.toHexString());
    doReturn(new Response(docRaw)).when(requestClient).doRequest(any());

    Document doc = auth.doAuthenticatedJsonRequest(reqBuilder.build(), Document.class);
    assertEquals(expectedObjectId, doc.getObjectId("_id"));
    assertEquals(42, (int) doc.getInteger("intValue"));

    doReturn(new Response(docRaw)).when(requestClient).doRequest(any());
    doc = auth.doAuthenticatedJsonRequest(reqBuilder.build(), new DocumentCodec());
    assertEquals(expectedObjectId, doc.getObjectId("_id"));
    assertEquals(42, (int) doc.getInteger("intValue"));

    // Check that BSON documents returned as extended JSON can be decoded as a custom type if
    // the codec is specifically provided.
    doReturn(new Response(docRaw)).when(requestClient).doRequest(any());
    final CustomType ct =
        auth.doAuthenticatedJsonRequest(reqBuilder.build(), new CustomType.Codec());
    assertEquals(expectedObjectId, ct.getId());
    assertEquals(42, ct.getIntValue());

    // Check that the correct exception is thrown if attempting to decode as a particular class
    // type if the auth was never configured to contain the provided class type
    // codec.
    doReturn(new Response(docRaw)).when(requestClient).doRequest(any());
    try {
      auth.doAuthenticatedJsonRequest(reqBuilder.build(), CustomType.class);
      fail();
    } catch (final StitchRequestException ignored) {
      // do nothing
    }

    // Check that BSON arrays can be decoded
    final List<Object> arrFromServer =
        Arrays.asList(21, "the meaning of life, the universe, and everything", 84, 168);
    final String arrFromServerRaw;
    try {
      arrFromServerRaw = StitchObjectMapper.getInstance().writeValueAsString(arrFromServer);
    } catch (JsonProcessingException e) {
      fail(e.getMessage());
      return;
    }
    doReturn(new Response(arrFromServerRaw)).when(requestClient).doRequest(any());

    @SuppressWarnings("unchecked")
    final List<Object> list = auth.doAuthenticatedJsonRequest(reqBuilder.build(), List.class);
    assertEquals(arrFromServer, list);
  }

  //
  @Test
  public void testDoAuthenticatedJsonRequestWithCustomCodecRegistry() {
    final StitchRequestClient requestClient = getMockedRequestClient();
    final StitchAuth auth =
        new StitchAuth(
            requestClient,
            new TestAuthRoutes(),
            new MemoryStorage(),
            CodecRegistries.fromRegistries(
                BsonUtils.DEFAULT_CODEC_REGISTRY,
                CodecRegistries.fromCodecs(new CustomType.Codec())));
    auth.loginWithCredentialBlocking(new CoreAnonymousAuthProviderClient().getCredential());

    final StitchAuthDocRequest.Builder reqBuilder = new StitchAuthDocRequest.Builder();
    reqBuilder.withPath("giveMeData");
    reqBuilder.withDocument(new Document());
    reqBuilder.withMethod(Method.POST);

    final String rawInt = "{\"$numberInt\": \"42\"}";
    // Check that primitive return types can be decoded.
    doReturn(new Response(rawInt)).when(requestClient).doRequest(any());
    assertEquals(42, (int) auth.doAuthenticatedJsonRequest(reqBuilder.build(), Integer.class));
    doReturn(new Response(rawInt)).when(requestClient).doRequest(any());
    assertEquals(42, (int) auth.doAuthenticatedJsonRequest(reqBuilder.build(), new IntegerCodec()));

    final ObjectId expectedObjectId = new ObjectId();
    final String docRaw =
        String.format(
            "{\"_id\": {\"$oid\": \"%s\"}, \"intValue\": {\"$numberInt\": \"42\"}}",
            expectedObjectId.toHexString());

    // Check that BSON documents returned as extended JSON can be decoded into BSON
    // documents.
    doReturn(new Response(docRaw)).when(requestClient).doRequest(any());
    Document doc = auth.doAuthenticatedJsonRequest(reqBuilder.build(), Document.class);
    assertEquals(expectedObjectId, doc.getObjectId("_id"));
    assertEquals(42, (int) doc.getInteger("intValue"));

    doReturn(new Response(docRaw)).when(requestClient).doRequest(any());
    doc = auth.doAuthenticatedJsonRequest(reqBuilder.build(), new DocumentCodec());
    assertEquals(expectedObjectId, doc.getObjectId("_id"));
    assertEquals(42, (int) doc.getInteger("intValue"));

    // Check that a custom type can be decoded without providing a codec, as long as that codec
    // is registered in the CoreStitchAuth's configuration.
    doReturn(new Response(docRaw)).when(requestClient).doRequest(any());
    final CustomType ct = auth.doAuthenticatedJsonRequest(reqBuilder.build(), CustomType.class);
    assertEquals(expectedObjectId, ct.getId());
    assertEquals(42, ct.getIntValue());
  }

  String getMockedUserId() {
    return "some-unique-user-id";
  }

  String getMockedAuthInfoResponse(final String accessToken, final String refreshToken) {
    final Map<String, String> apiAuthInfoMap = new HashMap<>();
    apiAuthInfoMap.put("user_id", getMockedUserId());
    apiAuthInfoMap.put("device_id", new ObjectId().toHexString());
    apiAuthInfoMap.put("access_token", accessToken);
    apiAuthInfoMap.put("refresh_token", refreshToken); // TODO: Separate token?

    try {
      return StitchObjectMapper.getInstance().writeValueAsString(apiAuthInfoMap);
    } catch (JsonProcessingException e) {
      fail(e.getMessage());
      return "";
    }
  }

  StitchRequestClient getMockedRequestClient() {
    final StitchRequestClient requestClient = Mockito.mock(StitchRequestClient.class);

    // Forward request to doRequest
    when(requestClient.doJsonRequestRaw(any()))
        .thenAnswer(inv -> requestClient.doRequest(inv.getArgument(0)));

    final String accessToken = DEFAULT_ACCESS_TOKEN;
    final String refreshToken = DEFAULT_REFRESH_TOKEN;

    // Any /login works
    doReturn(new Response(getMockedAuthInfoResponse(accessToken, refreshToken)))
        .when(requestClient)
        .doRequest(argThat(req -> req.getPath().endsWith("/login")));

    // Profile works if the access token is the same as the above
    doAnswer(
        inv -> {
          final StitchRequest req = inv.getArgument(0);
          assertEquals(
              "Bearer " + accessToken, req.getHeaders().getOrDefault("Authorization", ""));
          return new Response(getMockedUserProfile());
        })
        .when(requestClient)
        .doRequest(argThat(req -> req.getPath().endsWith("profile")));

    // Link works if the access token is the same as the above
    doAnswer(
        inv -> {
          final StitchRequest req = inv.getArgument(0);
          assertEquals(
              "Bearer " + accessToken, req.getHeaders().getOrDefault("Authorization", ""));
          return new Response(
              getMockedAuthInfoResponse(
                  accessToken, refreshToken)); // TODO: Really is different
        })
        .when(requestClient)
        .doRequest(argThat(req -> req.getPath().endsWith("/link")));

    return requestClient;
  }

  // TODO: Conform to spec
  String getMockedUserProfile() {
    final HashMap<String, Object> profile = new HashMap<>();
    profile.put("type", "foo");

    List<Map<String, String>> identities = new ArrayList<>();
    Map<String, String> identity1 = new HashMap<>();
    identity1.put("id", "bar");
    identity1.put("provider_type", "baz");
    identities.add(identity1);
    profile.put("identities", identities);

    profile.put("data", new HashMap<>());
    try {
      return StitchObjectMapper.getInstance().writeValueAsString(profile);
    } catch (JsonProcessingException e) {
      fail(e.getMessage());
      return "";
    }
  }

  static class TestAuthRoutes implements StitchAuthRoutes {

    @Override
    public String getSessionRoute() {
      return "session";
    }

    @Override
    public String getProfileRoute() {
      return "profile";
    }

    @Override
    public String getAuthProviderRoute(String providerName) {
      return providerName;
    }

    @Override
    public String getAuthProviderLoginRoute(String providerName) {
      return String.format("%s/login", providerName);
    }

    @Override
    public String getAuthProviderLinkRoute(String providerName) {
      return String.format("%s/link", providerName);
    }
  }

  public static class StitchAuth extends CoreStitchAuth<CoreStitchUserImpl> {
    protected StitchAuth(
        final StitchRequestClient requestClient,
        final StitchAuthRoutes authRoutes,
        final Storage storage) {
      super(requestClient, authRoutes, storage, false);
    }

    protected StitchAuth(
        final StitchRequestClient requestClient,
        final StitchAuthRoutes authRoutes,
        final Storage storage,
        final CodecRegistry codecRegistry) {
      super(requestClient, authRoutes, storage, codecRegistry, false);
    }

    @Override
    protected StitchUserFactory<CoreStitchUserImpl> getUserFactory() {
      return (String id,
          String loggedInProviderType,
          String loggedInProviderName,
          StitchUserProfileImpl userProfile) ->
          new CoreStitchUserImpl(id, loggedInProviderType, loggedInProviderName, userProfile) {};
    }

    @Override
    protected void onAuthEvent() {}

    @Override
    protected Document getDeviceInfo() {
      return null;
    }
  }
}
