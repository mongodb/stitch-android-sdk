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

import com.mongodb.stitch.core.StitchClientErrorCode;
import com.mongodb.stitch.core.StitchClientException;
import com.mongodb.stitch.core.StitchRequestErrorCode;
import com.mongodb.stitch.core.StitchRequestException;
import com.mongodb.stitch.core.StitchServiceErrorCode;
import com.mongodb.stitch.core.StitchServiceException;
import com.mongodb.stitch.core.auth.StitchCredential;
import com.mongodb.stitch.core.auth.internal.models.ApiCoreUserProfile;
import com.mongodb.stitch.core.internal.common.BsonUtils;
import com.mongodb.stitch.core.internal.common.IoUtils;
import com.mongodb.stitch.core.internal.common.StitchObjectMapper;
import com.mongodb.stitch.core.internal.common.Storage;
import com.mongodb.stitch.core.internal.net.Headers;
import com.mongodb.stitch.core.internal.net.Method;
import com.mongodb.stitch.core.internal.net.Response;
import com.mongodb.stitch.core.internal.net.StitchAuthDocRequest;
import com.mongodb.stitch.core.internal.net.StitchAuthRequest;
import com.mongodb.stitch.core.internal.net.StitchDocRequest;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;
import com.mongodb.stitch.core.internal.net.Stream;

import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import javax.annotation.meta.When;

import org.bson.Document;
import org.bson.codecs.Decoder;
import org.bson.codecs.configuration.CodecRegistry;

/**
 * CoreStitchAuth is responsible for authenticating clients as well as acting as a client for
 * authenticated requests. Synchronization in this class happens around the {@link
 * CoreStitchAuth#activeUserAuthInfo} and {@link CoreStitchAuth#activeUser} objects such that
 * access to them is 1. always atomic and 2. queued to prevent excess token refreshes.
 *
 * @param <StitchUserT> The type of users that will be consumed/produced by this component.
 */
public abstract class CoreStitchAuth<StitchUserT extends CoreStitchUser>
    implements StitchAuthRequestClient, Closeable {

  private final StitchRequestClient requestClient;
  private final StitchAuthRoutes authRoutes;
  private final Storage storage;
  private Thread refresherThread;
  private LinkedList<AuthInfo> loggedInUsersAuthInfoList;
  private StitchUserT activeUser;
  private AuthInfo activeUserAuthInfo;
  private Lock authLock;

  protected CoreStitchAuth(
      final StitchRequestClient requestClient,
      final StitchAuthRoutes authRoutes,
      final Storage storage,
      final boolean useTokenRefresher) {
    this.requestClient = requestClient;
    this.authRoutes = authRoutes;
    this.storage = storage;
    this.authLock = new ReentrantLock();

    final List<AuthInfo> loggedInUsersAuthInfo;
    try {
      loggedInUsersAuthInfo = AuthInfo.readCurrentUsersFromStorage(storage);
    } catch (final IOException e) {
      throw new StitchClientException(StitchClientErrorCode.COULD_NOT_LOAD_PERSISTED_AUTH_INFO);
    }

    this.loggedInUsersAuthInfoList = new LinkedList<>();
    if (loggedInUsersAuthInfo != null) {
      this.loggedInUsersAuthInfoList.addAll(loggedInUsersAuthInfo);
    }

    try {
      this.activeUserAuthInfo = AuthInfo.readActiveUserFromStorage(storage);
    } catch (final IOException e) {
      throw new StitchClientException(StitchClientErrorCode.COULD_NOT_LOAD_PERSISTED_AUTH_INFO);
    }

    if (this.activeUserAuthInfo == null) {
      this.activeUserAuthInfo = AuthInfo.empty();
    }

    if (this.activeUserAuthInfo.getUserId() != null) {
      this.activeUser = getUserFactory().makeUser(
          this.activeUserAuthInfo.getUserId(),
          this.activeUserAuthInfo.getDeviceId(),
          this.activeUserAuthInfo.getLoggedInProviderType(),
          this.activeUserAuthInfo.getLoggedInProviderName(),
          this.activeUserAuthInfo.getUserProfile(),
          this.activeUserAuthInfo.isLoggedIn());
    }

    if (useTokenRefresher) {
      refresherThread =
          new Thread(
              new AccessTokenRefresher<>(new WeakReference<>(this)),
              AccessTokenRefresher.class.getSimpleName());
      refresherThread.start();
    }
  }

  protected abstract StitchUserFactory<StitchUserT> getUserFactory();

  protected abstract void onAuthEvent();

  protected Document getDeviceInfo() {
    final Document info = new Document();
    if (hasDeviceId()) {
      info.put(DeviceFields.DEVICE_ID, getDeviceId());
    }
    return info;
  }

  @CheckReturnValue(when = When.NEVER)
  synchronized AuthInfo getAuthInfo() {
    return activeUserAuthInfo;
  }

  /**
   * Returns whether or not the client is logged in.
   *
   * @return whether or not the client is logged in.
   */
  @CheckReturnValue(when = When.NEVER)
  public synchronized boolean isLoggedIn() {
    return activeUser != null && activeUser.isLoggedIn();
  }

  /**
   * Returns the active logged in user.
  */
  @Nullable
  public synchronized StitchUserT getUser() {
    return activeUser;
  }

  public synchronized LinkedList<StitchUserT> listUsers() {
    final LinkedList<StitchUserT> userSet = new LinkedList<>();
    for (final AuthInfo authInfo : loggedInUsersAuthInfoList) {
      userSet.add(getUserFactory().makeUser(
          authInfo.getUserId(),
          authInfo.getDeviceId(),
          authInfo.getLoggedInProviderType(),
          authInfo.getLoggedInProviderName(),
          authInfo.getUserProfile(),
          authInfo.isLoggedIn()));
    }
    return userSet;
  }

  /**
   * Performs a request against Stitch using the provided {@link StitchAuthRequest} object, and
   * returns the response.
   *
   * @param stitchReq the request to perform.
   * @return the response to the request, successful or not.
   */
  public synchronized Response doAuthenticatedRequest(final StitchAuthRequest stitchReq) {
    try {
      if (!stitchReq.getHeaders().containsKey(Headers.AUTHORIZATION)) {
        return requestClient.doRequest(prepareAuthRequest(stitchReq, activeUserAuthInfo));
      }
      return requestClient.doRequest(stitchReq);
    } catch (final StitchServiceException ex) {
      return handleAuthFailure(ex, stitchReq);
    }
  }

  /**
   * Performs a request against Stitch using the provided {@link StitchAuthRequest} object,
   * and decodes the response using the provided result decoder.
   *
   * @param stitchReq The request to perform.
   * @return The response to the request, successful or not.
   */
  public <T> T doAuthenticatedRequest(final StitchAuthRequest stitchReq,
                                      final Decoder<T> resultDecoder) {
    final Response response = doAuthenticatedRequest(stitchReq);
    try {
      return BsonUtils.parseValue(IoUtils.readAllToString(response.getBody()), resultDecoder);
    } catch (final Exception e) {
      throw new StitchRequestException(e, StitchRequestErrorCode.DECODING_ERROR);
    }
  }

  /**
   * Performs a request against Stitch using the provided {@link StitchAuthRequest} object, and
   * decodes the JSON body of the response into a T value as specified by the provided class type.
   * The type will be decoded using the codec found for T in the codec registry given.
   * If the provided type is not supported by the codec registry to be used, the method will throw
   * a {@link org.bson.codecs.configuration.CodecConfigurationException}.
   *
   * @param stitchReq     the request to perform.
   * @param resultClass   the class that the JSON response should be decoded as.
   * @param codecRegistry the codec registry used for de/serialization.
   * @param <T>           the type into which the JSON response will be decoded into.
   * @return the decoded value.
   */
  public <T> T doAuthenticatedRequest(
      final StitchAuthRequest stitchReq,
      final Class<T> resultClass,
      final CodecRegistry codecRegistry
  ) {
    final Response response = doAuthenticatedRequest(stitchReq);

    try {
      return BsonUtils.parseValue(IoUtils.readAllToString(
          response.getBody()), resultClass, codecRegistry);
    } catch (final Exception e) {
      throw new StitchRequestException(e, StitchRequestErrorCode.DECODING_ERROR);
    }
  }

  @Override
  public <T> Stream<T> openAuthenticatedStream(final StitchAuthRequest stitchReq,
                                               final Decoder<T> decoder) {
    if (!isLoggedIn()) {
      throw new StitchClientException(StitchClientErrorCode.MUST_AUTHENTICATE_FIRST);
    }
    final String authToken = stitchReq.getUseRefreshToken()
        ? getAuthInfo().getRefreshToken() : getAuthInfo().getAccessToken();
    try {
      return new Stream<>(
          requestClient.doStreamRequest(stitchReq.builder().withPath(
              stitchReq.getPath() + AuthStreamFields.AUTH_TOKEN + authToken
          ).build()),
          decoder
      );
    } catch (final StitchServiceException ex) {
      return handleAuthFailureForStream(ex, stitchReq, decoder);
    }
  }

  public synchronized StitchUserT switchToUserWithId(final String userId) throws IllegalArgumentException {
    authLock.lock();
    try {
      for (final AuthInfo authInfo : loggedInUsersAuthInfoList) {
        if (authInfo.getUserId().equals(userId)) {
          this.activeUserAuthInfo = authInfo;
          this.activeUser = getUserFactory().makeUser(
              activeUserAuthInfo.getUserId(),
              activeUserAuthInfo.getDeviceId(),
              activeUserAuthInfo.getLoggedInProviderType(),
              activeUserAuthInfo.getLoggedInProviderName(),
              activeUserAuthInfo.getUserProfile(),
              activeUserAuthInfo.isLoggedIn());
          onAuthEvent();
          return this.activeUser;
        }
      }
    } finally {
      authLock.unlock();
    }

    throw new IllegalArgumentException(String.format("User with id %s not found", userId));
  }

  protected StitchUserT loginWithCredentialInternal(final StitchCredential credential) {
    authLock.lock();
    try {
      if (!isLoggedIn()) {
        return doLogin(credential, false);
      }

      for (final AuthInfo authInfo : loggedInUsersAuthInfoList) {
        if (credential.getProviderCapabilities().getReusesExistingSession()
            && credential.getProviderType().equals(authInfo.getLoggedInProviderType())) {
          return switchToUserWithId(authInfo.getUserId());
        }
      }


      return doLogin(credential, false);
    } finally {
      authLock.unlock();
    }
  }

  protected synchronized StitchUserT linkUserWithCredentialInternal(
      final CoreStitchUser user, final StitchCredential credential) {
    authLock.lock();
    try {
      if (user != activeUser) {
        throw new StitchClientException(StitchClientErrorCode.USER_NO_LONGER_VALID);
      }

      return doLogin(credential, true);
    } finally {
      authLock.unlock();
    }
  }

  protected synchronized void logoutInternal() {
    if (!isLoggedIn()) {
      return;
    }

    logoutInternal(activeUser.getId());
  }

  protected synchronized void logoutInternal(final String userId) throws IllegalArgumentException {
    authLock.lock();
    try {
      final AuthInfo authInfo = findAuthInfoById(userId);
      if (!authInfo.isLoggedIn()) {
        return;
      }

      try {
        doLogout(authInfo);
      } catch (final StitchServiceException ex) {
        // Do nothing
      } finally {
        clearUser(authInfo);
        // add logged out user to front of queue
        loggedInUsersAuthInfoList.addFirst(authInfo.loggedOut());
        try {
          AuthInfo.writeLoggedInUsersAuthInfoToStorage(loggedInUsersAuthInfoList, storage);
        } catch (final IOException e) {
          // Do nothing
        }
      }
    } finally {
      authLock.unlock();
    }
  }

  protected synchronized void removeUserInternal() {
    if (!isLoggedIn() || activeUser == null) {
      return;
    }

    removeUserInternal(activeUser.getId());
  }

  protected synchronized void removeUserInternal(final String userId) {
    authLock.lock();
    try {
      final AuthInfo authInfo = findAuthInfoById(userId);
      if (!authInfo.isLoggedIn()) {
        return;
      }
      try {
        doLogout(authInfo);
      } catch (final StitchServiceException ex) {
        // Do nothing
      } finally {
        clearUser(authInfo);
      }
    } finally {
      authLock.unlock();
    }
  }

  protected synchronized boolean hasDeviceId() {
    return activeUserAuthInfo.getDeviceId() != null
        && !activeUserAuthInfo.getDeviceId().isEmpty()
        && !activeUserAuthInfo.getDeviceId().equals("000000000000000000000000");
  }

  protected synchronized String getDeviceId() {
    if (!hasDeviceId()) {
      return null;
    }
    return activeUserAuthInfo.getDeviceId();
  }

  private static StitchAuthRequest prepareAuthRequest(final StitchAuthRequest stitchReq,
                                                      final AuthInfo authInfo) {
    if (!authInfo.isLoggedIn()) {
      throw new StitchClientException(StitchClientErrorCode.MUST_AUTHENTICATE_FIRST);
    }

    final StitchAuthRequest.Builder newReq = stitchReq.builder();
    final Map<String, String> newHeaders = newReq.getHeaders(); // This is not a copy
    if (stitchReq.getUseRefreshToken()) {
      newHeaders.put(
          Headers.AUTHORIZATION, Headers.getAuthorizationBearer(authInfo.getRefreshToken()));
    } else {
      newHeaders.put(
          Headers.AUTHORIZATION, Headers.getAuthorizationBearer(authInfo.getAccessToken()));
    }
    newReq.withHeaders(newHeaders);
    newReq.withTimeout(stitchReq.getTimeout());
    return newReq.build();
  }

  private <T> Stream<T> handleAuthFailureForStream(final StitchServiceException ex,
                                                   final StitchAuthRequest req,
                                                   final Decoder<T> decoder) {
    if (ex.getErrorCode() != StitchServiceErrorCode.INVALID_SESSION) {
      throw ex;
    }

    // using a refresh token implies we cannot refresh anything, so clear auth and
    // notify
    if (req.getUseRefreshToken() || !req.getShouldRefreshOnFailure()) {
      clearActiveUser();
      throw ex;
    }

    tryRefreshAccessToken(req.getStartedAt());

    return openAuthenticatedStream(
        req.builder().withShouldRefreshOnFailure(false).build(), decoder);
  }

  private synchronized Response handleAuthFailure(final StitchServiceException ex, final StitchAuthRequest req) {
    if (ex.getErrorCode() != StitchServiceErrorCode.INVALID_SESSION) {
      throw ex;
    }

    // using a refresh token implies we cannot refresh anything, so clear auth and
    // notify
    if (req.getUseRefreshToken() || !req.getShouldRefreshOnFailure()) {
      clearActiveUser();
      throw ex;
    }

    tryRefreshAccessToken(req.getStartedAt());

    return doAuthenticatedRequest(
        prepareAuthRequest(
            req.builder().withShouldRefreshOnFailure(false).build(),
            activeUserAuthInfo));
  }

  // use this critical section to create a queue of pending outbound requests
  // that should wait on the result of doing a token refresh or logoutUserWithId. This will
  // prevent too many refreshes happening one after the other.
  private void tryRefreshAccessToken(final Long reqStartedAt) {
    authLock.lock();
    try {
      if (!isLoggedIn()) {
        throw new StitchClientException(StitchClientErrorCode.LOGGED_OUT_DURING_REQUEST);
      }

      try {
        final Jwt jwt = Jwt.fromEncoded(getAuthInfo().getAccessToken());
        if (jwt.getIssuedAt() >= reqStartedAt) {
          return;
        }
      } catch (final IOException e) {
        // Swallow
      }

      // retry
      refreshAccessToken();
    } finally {
      authLock.unlock();
    }
  }

  synchronized void refreshAccessToken() {
    authLock.lock();
    try {
      final StitchAuthRequest.Builder reqBuilder = new StitchAuthRequest.Builder();
      reqBuilder
          .withRefreshToken()
          .withPath(authRoutes.getSessionRoute())
          .withMethod(Method.POST);

      final Response response = doAuthenticatedRequest(
          prepareAuthRequest(reqBuilder.build(), activeUserAuthInfo));

      try {
        final AuthInfo partialInfo = AuthInfo.readFromApi(response.getBody());
        activeUserAuthInfo = getAuthInfo().merge(partialInfo);
      } catch (final IOException e) {
        throw new StitchRequestException(e, StitchRequestErrorCode.DECODING_ERROR);
      }

      try {
        AuthInfo.writeActiveUserAuthInfoToStorage(activeUserAuthInfo, storage);
      } catch (final IOException e) {
        throw new StitchClientException(StitchClientErrorCode.COULD_NOT_PERSIST_AUTH_INFO);
      }
    } finally {
      authLock.unlock();
    }
  }

  private void attachAuthOptions(final Document authBody) {
    final Document options = new Document();
    options.put(AuthLoginFields.DEVICE, getDeviceInfo());
    authBody.put(AuthLoginFields.OPTIONS, options);
  }

  // callers of doLogin should be serialized before calling in.
  private StitchUserT doLogin(final StitchCredential credential, final boolean asLinkRequest) {
    final Response response = doLoginRequest(credential, asLinkRequest);
    final StitchUserT user = processLoginResponse(credential, response, asLinkRequest);
    onAuthEvent();
    return user;
  }

  private Response doLoginRequest(final StitchCredential credential, final boolean asLinkRequest) {
    final StitchDocRequest.Builder reqBuilder = new StitchDocRequest.Builder();
    reqBuilder.withMethod(Method.POST);

    if (asLinkRequest) {
      reqBuilder.withPath(authRoutes.getAuthProviderLinkRoute(credential.getProviderName()));
    } else {
      reqBuilder.withPath(authRoutes.getAuthProviderLoginRoute(credential.getProviderName()));
    }

    final Document material = credential.getMaterial();
    final Document body = material == null ? new Document() : material;
    attachAuthOptions(body);
    reqBuilder.withDocument(body);

    if (!asLinkRequest) {
      return requestClient.doRequest(reqBuilder.build());
    }
    final StitchAuthDocRequest linkRequest =
        new StitchAuthDocRequest(reqBuilder.build(), reqBuilder.getDocument());
    return doAuthenticatedRequest(prepareAuthRequest(linkRequest, activeUserAuthInfo));
  }

  private StitchUserT processLoginResponse(
      final StitchCredential credential, final Response response, final boolean asLinkRequest) {
    AuthInfo newAuthInfo;
    try {
      newAuthInfo = AuthInfo.readFromApi(response.getBody());
    } catch (final IOException e) {
      throw new StitchRequestException(e, StitchRequestErrorCode.DECODING_ERROR);
    }

    // Preserve old auth info in case of profile request failure
    final AuthInfo oldActiveUserInfo = activeUserAuthInfo;
    final StitchUserT oldActiveUser = activeUser;

    newAuthInfo =
        activeUserAuthInfo.merge(
            new AuthInfo(
                newAuthInfo.getUserId(),
                newAuthInfo.getDeviceId(),
                newAuthInfo.getAccessToken(),
                newAuthInfo.getRefreshToken(),
                credential.getProviderType(),
                credential.getProviderName(),
                null));

    // Provisionally set so we can make a profile request
    activeUserAuthInfo = newAuthInfo;
    activeUser =
        getUserFactory()
            .makeUser(
                activeUserAuthInfo.getUserId(),
                activeUserAuthInfo.getDeviceId(),
                credential.getProviderType(),
                credential.getProviderName(),
                null,
                activeUserAuthInfo.isLoggedIn());

    final StitchUserProfileImpl profile;
    try {
      profile = doGetUserProfile(activeUserAuthInfo);
    } catch (final Exception ex) {
      // If this was a link request or another user is logged in,
      // back out of setting authInfo and reset any created user. This
      // will keep the currently logged in user logged in if the profile
      // request failed, and in this particular edge case the user is
      // linked, but they are logged in with their older credentials.
      if (asLinkRequest || !loggedInUsersAuthInfoList.isEmpty()) {
        activeUserAuthInfo = oldActiveUserInfo;
        activeUser = oldActiveUser;
      } else { // otherwise if this was a normal login request, log the user out
        clearActiveUser();
      }

      throw ex;
    }

    // Finally set the info and user
    newAuthInfo =
        newAuthInfo.merge(
            new AuthInfo(
                newAuthInfo.getUserId(),
                newAuthInfo.getDeviceId(),
                newAuthInfo.getAccessToken(),
                newAuthInfo.getRefreshToken(),
                credential.getProviderType(),
                credential.getProviderName(),
                profile));

    try {
      AuthInfo.writeActiveUserAuthInfoToStorage(newAuthInfo, storage);

      // if this is a link request, remove the old active info
      // and replace it with the updated version
      if (asLinkRequest || loggedInUsersAuthInfoList.contains(newAuthInfo)) {
        loggedInUsersAuthInfoList.remove(oldActiveUserInfo);
      }

      loggedInUsersAuthInfoList.add(newAuthInfo);
      AuthInfo.writeLoggedInUsersAuthInfoToStorage(loggedInUsersAuthInfoList, storage);
    } catch (final IOException e) {
      // Back out of setting authInfo
      activeUserAuthInfo = oldActiveUserInfo;
      activeUser = null;
      loggedInUsersAuthInfoList.remove(newAuthInfo);
      throw new StitchClientException(StitchClientErrorCode.COULD_NOT_PERSIST_AUTH_INFO);
    }

    activeUserAuthInfo = newAuthInfo;
    activeUser =
        getUserFactory()
            .makeUser(
                activeUserAuthInfo.getUserId(),
                activeUserAuthInfo.getDeviceId(),
                credential.getProviderType(),
                credential.getProviderName(),
                profile,
                activeUserAuthInfo.isLoggedIn());

    return activeUser;
  }

  private StitchUserProfileImpl doGetUserProfile(final AuthInfo authInfo) {
    final StitchAuthRequest.Builder reqBuilder = new StitchAuthRequest.Builder();
    reqBuilder.withMethod(Method.GET).withPath(authRoutes.getProfileRoute());

    final Response response = doAuthenticatedRequest(
        prepareAuthRequest(reqBuilder.build(), authInfo));

    try {
      return StitchObjectMapper.getInstance()
          .readValue(response.getBody(), ApiCoreUserProfile.class);
    } catch (final IOException e) {
      throw new StitchRequestException(e, StitchRequestErrorCode.DECODING_ERROR);
    }
  }

  private void doLogout(final AuthInfo authInfo) {
    final StitchAuthRequest.Builder reqBuilder = new StitchAuthRequest.Builder();
    reqBuilder.withRefreshToken().withPath(authRoutes.getSessionRoute()).withMethod(Method.DELETE);
    this.doAuthenticatedRequest(prepareAuthRequest(reqBuilder.build(), authInfo));
  }

  private synchronized void clearActiveUser() {
    if (!isLoggedIn()) {
      return;
    }

    loggedInUsersAuthInfoList.remove(activeUserAuthInfo);

    activeUserAuthInfo = activeUserAuthInfo.loggedOut();
    activeUser = null;

    try {
      AuthInfo.writeActiveUserAuthInfoToStorage(activeUserAuthInfo, storage);
    } catch (final IOException e) {
      throw new StitchClientException(StitchClientErrorCode.COULD_NOT_PERSIST_AUTH_INFO);
    }
    onAuthEvent();
  }

  private synchronized void clearUser(final AuthInfo authInfo) {
    try {
      if (loggedInUsersAuthInfoList.remove(authInfo)) {
        AuthInfo.writeLoggedInUsersAuthInfoToStorage(loggedInUsersAuthInfoList, storage);
      }
      if (activeUserAuthInfo.equals(authInfo)) {
        clearActiveUser();
      }
    } catch (final IOException e) {
      throw new StitchClientException(StitchClientErrorCode.COULD_NOT_PERSIST_AUTH_INFO);
    }
  }

  /**
   * Closes the component down by stopping the access token refresher.
   */
  public void close() throws IOException {
    if (refresherThread != null) {
      refresherThread.interrupt();
    }
    requestClient.close();
  }

  protected StitchRequestClient getRequestClient() {
    return requestClient;
  }

  protected StitchAuthRoutes getAuthRoutes() {
    return authRoutes;
  }

  private AuthInfo findAuthInfoById(final String userId) throws IllegalArgumentException {
    for (final AuthInfo authInfo : loggedInUsersAuthInfoList) {
      if (authInfo.getUserId().equals(userId)) {
        return authInfo;
      }
    }

    throw new IllegalArgumentException("user id not found");
  }

  private static class AuthStreamFields {
    static final String AUTH_TOKEN = "&stitch_at=";
  }

  private static class AuthLoginFields {
    static final String OPTIONS = "options";
    static final String DEVICE = "device";
  }
}
