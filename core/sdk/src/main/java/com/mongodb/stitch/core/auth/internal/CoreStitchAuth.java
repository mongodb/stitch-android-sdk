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
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousAuthProvider;
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
import java.util.LinkedHashMap;
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
  private LinkedHashMap<String, AuthInfo> allUsersAuthInfo;
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

    final List<AuthInfo> allUsersAuthInfoList;
    try {
      allUsersAuthInfoList = AuthInfo.readCurrentUsersFromStorage(storage);
    } catch (final IOException e) {
      throw new StitchClientException(StitchClientErrorCode.COULD_NOT_LOAD_PERSISTED_AUTH_INFO);
    }

    this.allUsersAuthInfo = new LinkedHashMap<>();
    if (allUsersAuthInfoList != null) {
      for (final AuthInfo authInfo : allUsersAuthInfoList) {
        this.allUsersAuthInfo.put(authInfo.getUserId(), authInfo);
      }
    }

    try {
      this.activeUserAuthInfo = AuthInfo.readActiveUserFromStorage(storage);
    } catch (final IOException e) {
      throw new StitchClientException(StitchClientErrorCode.COULD_NOT_LOAD_PERSISTED_AUTH_INFO);
    }

    if (this.activeUserAuthInfo == null) {
      this.activeUserAuthInfo = AuthInfo.empty();
    }

    if (this.activeUserAuthInfo.hasUser()) {
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
    for (final AuthInfo authInfo : this.allUsersAuthInfo.values()) {
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
    return doAuthenticatedRequest(stitchReq, activeUserAuthInfo);
  }

  /**
   * Internal method which performs the authenticated request by preparing the auth request with
   * the provided auth info and request.
   */
  private synchronized Response doAuthenticatedRequest(
      final StitchAuthRequest stitchReq,
      final AuthInfo authInfo
  ) {
    try {
      return requestClient.doRequest(prepareAuthRequest(stitchReq, authInfo));
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

  public synchronized StitchUserT switchToUserWithId(final String userId)
      throws StitchClientException {
    authLock.lock();
    try {
      final AuthInfo authInfo = findAuthInfoById(userId);
      if (!authInfo.isLoggedIn()) {
        throw new StitchClientException(StitchClientErrorCode.USER_NOT_LOGGED_IN);
      }

      // persist auth info storage before actually setting auth state so that
      // if the persist call throws, we are not in an inconsistent state
      // with storage
      try {
        AuthInfo.writeActiveUserAuthInfoToStorage(authInfo, this.storage);
      } catch (IOException e) {
        throw new StitchClientException(StitchClientErrorCode.COULD_NOT_PERSIST_AUTH_INFO);
      }

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
    } finally {
      authLock.unlock();
    }
  }

  protected StitchUserT loginWithCredentialInternal(final StitchCredential credential) {
    authLock.lock();
    try {
      if (!isLoggedIn()) {
        return doLogin(credential, false);
      }

      if (credential.getProviderCapabilities().getReusesExistingSession()) {
        for (final AuthInfo authInfo : this.allUsersAuthInfo.values()) {
          if (authInfo.getLoggedInProviderType().equals(credential.getProviderType())) {
            return switchToUserWithId(authInfo.getUserId());
          }
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

    logoutUserWithIdInternal(activeUser.getId());
  }

  protected synchronized void logoutUserWithIdInternal(
      final String userId
  ) throws StitchClientException {
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
        clearUserAuth(authInfo.getUserId());

        // if the user was anonymous, delete the user, since you can't log back
        // in to an anonymous user after they have logged out.
        if (authInfo.getLoggedInProviderType().equals(AnonymousAuthProvider.TYPE)) {
          this.removeUserWithIdInternal(userId);
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

    removeUserWithIdInternal(activeUser.getId());
  }

  protected synchronized void removeUserWithIdInternal(final String userId) {
    authLock.lock();
    try {
      final AuthInfo authInfo = findAuthInfoById(userId);
      try {
        if (authInfo.isLoggedIn()) {
          doLogout(authInfo);
        }
      } catch (final StitchServiceException ex) {
        // Do nothing
      }

      clearUserAuth(authInfo.getUserId());
      this.allUsersAuthInfo.remove(userId);

      try {
        AuthInfo.writeCurrentUsersToStorage(this.allUsersAuthInfo.values(), this.storage);
      } catch (IOException e) {
        throw new StitchClientException(StitchClientErrorCode.COULD_NOT_PERSIST_AUTH_INFO);
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
      clearActiveUserAuth();
      throw ex;
    }

    tryRefreshAccessToken(req.getStartedAt());

    return openAuthenticatedStream(
        req.builder().withShouldRefreshOnFailure(false).build(), decoder);
  }

  private synchronized Response handleAuthFailure(final StitchServiceException ex,
                                                  final StitchAuthRequest req) {
    if (ex.getErrorCode() != StitchServiceErrorCode.INVALID_SESSION) {
      throw ex;
    }

    // using a refresh token implies we cannot refresh anything, so clear auth and
    // notify
    if (req.getUseRefreshToken() || !req.getShouldRefreshOnFailure()) {
      clearActiveUserAuth();
      throw ex;
    }

    tryRefreshAccessToken(req.getStartedAt());

    return doAuthenticatedRequest(req.builder().withShouldRefreshOnFailure(false).build());
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

      final Response response = doAuthenticatedRequest(reqBuilder.build(), activeUserAuthInfo);

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
    return doAuthenticatedRequest(linkRequest);
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
      profile = doGetUserProfile();
    } catch (final Exception ex) {
      // If this was a link request or there was an active user logged in,
      // back out of setting authInfo and reset any created user. This
      // will keep the currently logged in user logged in if the profile
      // request failed, and in this particular edge case the user is
      // linked, but they are logged in with their older credentials.
      if (asLinkRequest || oldActiveUserInfo.hasUser()) {
        final AuthInfo linkedAuthInfo = activeUserAuthInfo;
        activeUserAuthInfo = oldActiveUserInfo;
        activeUser = oldActiveUser;

        // to prevent the case where this user gets removed when logged out
        // in the future because the original provider type was anonymous,
        // make sure the auth info reflects the new logged in provider type
        if (asLinkRequest) {
          this.activeUserAuthInfo = this.activeUserAuthInfo.withAuthProvider(
              linkedAuthInfo.getLoggedInProviderType(),
              linkedAuthInfo.getLoggedInProviderName()
          );
        }
      } else { // otherwise if this was a normal login request, log the user out
        clearActiveUserAuth();
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

      // this replaces any old info that may have
      // existed for this user if this was a link request, or if this
      // user already existed in the list of all users
      this.allUsersAuthInfo.put(newAuthInfo.getUserId(), newAuthInfo);

      AuthInfo.writeCurrentUsersToStorage(allUsersAuthInfo.values(), storage);
    } catch (final IOException e) {
      // Back out of setting authInfo with this new user
      activeUserAuthInfo = oldActiveUserInfo;
      activeUser = null;

      // delete the new partial auth info from the list of all users if
      // if the new auth info is not the same user as the older user
      if (!newAuthInfo.getUserId().equals(oldActiveUserInfo.getUserId())
           && newAuthInfo.getUserId() != null) {
        this.allUsersAuthInfo.remove(newAuthInfo.getUserId());
      }

      throw new StitchClientException(StitchClientErrorCode.COULD_NOT_PERSIST_AUTH_INFO);
    }

    // set the active user info to the new auth info and new user with profile
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

  private StitchUserProfileImpl doGetUserProfile() {
    final StitchAuthRequest.Builder reqBuilder = new StitchAuthRequest.Builder();
    reqBuilder.withMethod(Method.GET).withPath(authRoutes.getProfileRoute());

    final Response response = doAuthenticatedRequest(reqBuilder.build());

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
    this.doAuthenticatedRequest(reqBuilder.build(), authInfo);
  }

  private synchronized void clearActiveUserAuth() {
    if (!isLoggedIn()) {
      return;
    }

    this.clearUserAuth(activeUserAuthInfo.getUserId());
  }

  private synchronized void clearUserAuth(final String userId) {
    final AuthInfo unclearedAuthInfo = this.allUsersAuthInfo.get(userId);
    if (unclearedAuthInfo == null) {
      // this doesn't necessarily mean there's an error. we could be in a
      // provisional state where the profile request failed and we're just
      // trying to log out the active user.
      if (this.activeUserAuthInfo.getUserId() != userId) {
        // only throw if this ID is not the active user either
        throw new StitchClientException(StitchClientErrorCode.USER_NOT_FOUND);
      }
    }

    try {
      if (unclearedAuthInfo != null) {
        this.allUsersAuthInfo.put(userId, unclearedAuthInfo.loggedOut());
        AuthInfo.writeCurrentUsersToStorage(this.allUsersAuthInfo.values(), storage);
      }

      // if the auth info we're clearing is also the active user's auth info,
      // clear the active user's auth as well
      if (activeUserAuthInfo.hasUser() && activeUserAuthInfo.getUserId().equals(userId)) {
        this.activeUserAuthInfo = this.activeUserAuthInfo.withClearedUser();
        this.activeUser = null;

        AuthInfo.writeActiveUserAuthInfoToStorage(this.activeUserAuthInfo, this.storage);
        this.onAuthEvent();
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

  private AuthInfo findAuthInfoById(final String userId) throws StitchClientException {
    final AuthInfo authInfo = this.allUsersAuthInfo.get(userId);
    if (authInfo == null) {
      throw new StitchClientException(StitchClientErrorCode.USER_NOT_FOUND);
    }

    return authInfo;
  }

  private static class AuthStreamFields {
    static final String AUTH_TOKEN = "&stitch_at=";
  }

  private static class AuthLoginFields {
    static final String OPTIONS = "options";
    static final String DEVICE = "device";
  }
}
