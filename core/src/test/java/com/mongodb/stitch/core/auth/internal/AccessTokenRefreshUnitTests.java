package com.mongodb.stitch.core.auth.internal;

import org.junit.Test;
import org.mockito.Mockito;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Date;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


public class AccessTokenRefreshUnitTests {

    @Test
    public void testCheckRefresh() {

        @SuppressWarnings("unchecked")
        final CoreStitchAuth<CoreStitchUser> auth = (CoreStitchAuth<CoreStitchUser>) Mockito.mock(CoreStitchAuth.class);

        AccessTokenRefresher<CoreStitchUser> accessTokenRefresher = new AccessTokenRefresher<>(
                new WeakReference<>(auth)
        );

        // Auth starts out logged in and with a fresh token
        final String freshJWT =  Jwts.builder()
                .setIssuedAt(new Date())
                .setSubject("uniqueUserID")
                .setExpiration(new Date(((Calendar.getInstance().getTimeInMillis() + (20*60*1000)))))
                .signWith(SignatureAlgorithm.HS256,
                        "abcdefghijklmnopqrstuvwxyz1234567890".getBytes()).compact();
        final AuthInfo freshAuthInfo = new AuthInfo(
                "",
                "",
                freshJWT,
                freshJWT,
                "",
                "",
                null
        );
        doReturn(true).when(auth).isLoggedIn();
        doReturn(freshAuthInfo).when(auth).getAuthInfo();
        verify(auth, times(0)).refreshAccessToken();
        verify(auth, times(0)).getAuthInfo();

        accessTokenRefresher.checkRefresh();
        verify(auth, times(0)).refreshAccessToken();
        verify(auth, times(1)).getAuthInfo();

        // Auth info is now expired
        final String expiredJWT =  Jwts.builder()
                .setIssuedAt(new Date(((Calendar.getInstance().getTimeInMillis() - (10*60*1000)))))
                .setSubject("uniqueUserID")
                .setExpiration(new Date(((Calendar.getInstance().getTimeInMillis() - (5*60*1000)))))
                .signWith(SignatureAlgorithm.HS256,
                        "abcdefghijklmnopqrstuvwxyz1234567890".getBytes()).compact();

        final AuthInfo expiredAuthInfo = new AuthInfo(
                "",
                "",
                expiredJWT,
                expiredJWT,
                "",
                "",
                null
        );
        doReturn(expiredAuthInfo).when(auth).getAuthInfo();

        accessTokenRefresher.checkRefresh();
        verify(auth, times(1)).refreshAccessToken();
        verify(auth, times(2)).getAuthInfo();
    }
}
