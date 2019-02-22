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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;
import org.junit.Test;
import org.mockito.Mockito;

public class AccessTokenRefreshUnitTests {

  @Test
  public void testCheckRefresh() {

    @SuppressWarnings("unchecked")
    final CoreStitchAuth<CoreStitchUser> auth =
        (CoreStitchAuth<CoreStitchUser>) Mockito.mock(CoreStitchAuth.class);

    final AccessTokenRefresher<CoreStitchUser> accessTokenRefresher =
        new AccessTokenRefresher<>(new WeakReference<>(auth));

    // Auth starts out logged in and with a fresh token
    final String freshJwt =
        Jwts.builder()
            .setIssuedAt(new Date())
            .setSubject("uniqueUserID")
            .setExpiration(
                new Date(((Calendar.getInstance().getTimeInMillis() + (20 * 60 * 1000)))))
            .signWith(
                SignatureAlgorithm.HS256,
                "abcdefghijklmnopqrstuvwxyz1234567890".getBytes(StandardCharsets.UTF_8))
            .compact();
    final AuthInfo freshAuthInfo = new AuthInfo("", "", freshJwt, freshJwt, "", "", null, null);
    doReturn(true).when(auth).isLoggedIn();
    doReturn(freshAuthInfo).when(auth).getAuthInfo();
    verify(auth, times(0)).refreshAccessToken();
    verify(auth, times(0)).getAuthInfo();

    assertTrue(accessTokenRefresher.checkRefresh());
    verify(auth, times(0)).refreshAccessToken();
    verify(auth, times(1)).getAuthInfo();

    // Auth info is now expired
    final String expiredJwt =
        Jwts.builder()
            .setIssuedAt(new Date(((Calendar.getInstance().getTimeInMillis() - (10 * 60 * 1000)))))
            .setSubject("uniqueUserID")
            .setExpiration(new Date(((Calendar.getInstance().getTimeInMillis() - (5 * 60 * 1000)))))
            .signWith(
                SignatureAlgorithm.HS256,
                "abcdefghijklmnopqrstuvwxyz1234567890".getBytes(StandardCharsets.UTF_8))
            .compact();

    final AuthInfo expiredAuthInfo = new AuthInfo("", "", expiredJwt, expiredJwt, "",
            "", null, null);
    doReturn(expiredAuthInfo).when(auth).getAuthInfo();

    assertTrue(accessTokenRefresher.checkRefresh());
    verify(auth, times(1)).refreshAccessToken();
    verify(auth, times(2)).getAuthInfo();

    // Auth info is gone after checking is logged in
    doReturn(null).when(auth).getAuthInfo();
    assertTrue(accessTokenRefresher.checkRefresh());
    verify(auth, times(1)).refreshAccessToken();
    verify(auth, times(3)).getAuthInfo();

    // CoreStitchAuth is GCd
    final AccessTokenRefresher<CoreStitchUser> accessTokenRefresher2 =
        new AccessTokenRefresher<>(new WeakReference<>(null));
    assertFalse(accessTokenRefresher2.checkRefresh());
  }
}
