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

package com.mongodb.stitch.core.testutils;

import com.mongodb.stitch.core.auth.UserType;
import com.mongodb.stitch.core.auth.internal.models.ApiAuthInfo;
import com.mongodb.stitch.core.auth.internal.models.ApiCoreUserProfile;
import com.mongodb.stitch.core.auth.internal.models.ApiStitchUserIdentity;
import com.mongodb.stitch.core.internal.net.Response;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class ApiTestUtils {
  private static final String TEST_ACCESS_TOKEN = getTestAccessToken();
  private static final String TEST_REFRESH_TOKEN = getTestRefreshToken();

  private static int uniqueUserId = -1;
  private static int uniqueDeviceId = -1;

  /**
   * Gets an access token JWT for testing that is always the same.
   */
  public static String getTestAccessToken() {
    if (TEST_ACCESS_TOKEN != null) {
      return TEST_ACCESS_TOKEN;
    }
    final Map<String, Object> claims = new HashMap<>();
    claims.put("typ", "access");
    return Jwts.builder()
        .setClaims(claims)
        .setIssuedAt(Date.from(Instant.now().minus(Duration.ofHours(1))))
        .setSubject("uniqueUserID")
        .setExpiration(new Date(((Calendar.getInstance().getTimeInMillis() + (5 * 60 * 1000)))))
        .signWith(
            SignatureAlgorithm.HS256,
            "abcdefghijklmnopqrstuvwxyz1234567890".getBytes(StandardCharsets.UTF_8))
        .compact();
  }

  /**
   * Gets an refresh token JWT for testing that is always the same.
   */
  public static String getTestRefreshToken() {
    if (TEST_REFRESH_TOKEN != null) {
      return TEST_REFRESH_TOKEN;
    }
    final Map<String, Object> claims = new HashMap<>();
    claims.put("typ", "refresh");
    return Jwts.builder()
        .setClaims(claims)
          .setIssuedAt(Date.from(Instant.now().minus(Duration.ofHours(1))))
        .setSubject("uniqueUserID")
        .setExpiration(new Date(((Calendar.getInstance().getTimeInMillis() + (5 * 60 * 1000)))))
        .setClaims(claims)
        .signWith(
            SignatureAlgorithm.HS256,
            "abcdefghijklmnopqrstuvwxyz1234567890".getBytes(StandardCharsets.UTF_8))
        .compact();
  }

  public static String getLastUserId() {
    return Integer.toString(uniqueUserId);
  }

  public static String getLastDeviceId() {
    return Integer.toString(uniqueDeviceId);
  }

  /**
   * Gets a login response for testing that increments device and user id by one.
   */
  private static ApiAuthInfo getTestLoginResponse() {
    uniqueUserId++;
    uniqueDeviceId++;

    return new ApiAuthInfo(
        Integer.toString(uniqueUserId),
        Integer.toString(uniqueDeviceId),
        getTestAccessToken(),
        getTestRefreshToken());
  }

  /**
   * Gets a link response for testing that corresponds with the last login response.
   */
  public static ApiAuthInfo getTestLinkResponse() {
    return new ApiAuthInfo(
        getLastUserId(),
        getLastDeviceId(),
        getTestAccessToken(),
        null);
  }

  /**
   * Gets a mocked request client for testing that can be extended. In general
   * it supports enough to return responses for login, profile, and link. Anything else
   * will return null {@link Response}s.
   */
  public static StitchRequestClient getMockedRequestClient() {
    final StitchRequestClient requestClient = Mockito.mock(StitchRequestClient.class);

    // Any /login works
    Mockito.doAnswer((ignored) -> new Response(getTestLoginResponse().toString()))
        .when(requestClient)
        .doRequest(ArgumentMatchers.argThat(req -> req.getPath().endsWith("/login")));

    // Profile works if the access token is the same as the above
    Mockito.doAnswer((ignored) -> new Response(getTestUserProfile().toString()))
        .when(requestClient)
        .doRequest(ArgumentMatchers.argThat(req -> req.getPath().endsWith("/profile")));

    // Link works if the access token is the same as the above
    Mockito.doAnswer((ignored) -> new Response(getTestLinkResponse().toString()))
        .when(requestClient)
        .doRequest(ArgumentMatchers.argThat(req -> req.getPath().endsWith("/login?link=true")));

    return requestClient;
  }

  /**
   * Gets a user profile for testing that is always the same.
   */
  public static ApiCoreUserProfile getTestUserProfile() {
    final List<ApiStitchUserIdentity> identities = new ArrayList<>();
    identities.add(new ApiStitchUserIdentity("bar", "baz"));
    return new ApiCoreUserProfile(
        UserType.NORMAL.toString(),
        new HashMap<>(),
        identities);
  }

  public static String getAuthorizationBearer(final String accessToken) {
    return "Bearer " + accessToken;
  }
}
