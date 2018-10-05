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
import com.mongodb.stitch.core.internal.net.StitchRequest;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;
import com.mongodb.stitch.core.internal.net.Stream;

import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Map;

import javax.annotation.CheckReturnValue;
import javax.annotation.meta.When;

import org.bson.Document;
import org.bson.codecs.Decoder;
import org.bson.codecs.configuration.CodecRegistry;

/**
 * CoreStitchAuth is responsible for authenticating clients as well as acting as a client for
 * authenticated requests. Synchronization in this class happens around the {@link
 * CoreStitchAuth#authInfo} and {@link CoreStitchAuth#currentUser} objects such that access to them
 * is 1. always atomic and 2. queued to prevent excess token refreshes.
 *
 * @param <StitchUserT> The type of users that will be consumed/produced by this component.
 */
public abstract class CoreStitchAuth<StitchUserT extends CoreStitchUser>
    implements StitchAuthRequestClient, Closeable {

  private final StitchRequestClient requestClient;
  private final StitchAuthRoutes authRoutes;
  private final Storage storage;
  private Thread refresherThread;
  private AuthInfo authInfo;
  private StitchUserT currentUser;

  protected CoreStitchAuth(
      final StitchRequestClient requestClient,
      final StitchAuthRoutes authRoutes,
      final Storage storage,
      final boolean useTokenRefresher) {
    this.requestClient = requestClient;
    this.authRoutes = authRoutes;
    this.storage = storage;

    final AuthInfo info;
    try {
      info = AuthInfo.readFromStorage(storage);
    } catch (final IOException e) {
      throw new StitchClientException(StitchClientErrorCode.COULD_NOT_LOAD_PERSISTED_AUTH_INFO);
    }
    if (info == null) {
      this.authInfo = AuthInfo.empty();
    } else {
      this.authInfo = info;
    }

    if (this.authInfo.getUserId() != null) {
      // this implies other properties we are interested should be set
      this.currentUser =
          getUserFactory()
              .makeUser(
                  this.authInfo.getUserId(),
                  this.authInfo.getLoggedInProviderType(),
                  this.authInfo.getLoggedInProviderName(),
                  this.authInfo.getUserProfile());
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
    return authInfo;
  }

  /**
   * Returns whether or not the client is logged in.
   *
   * @return whether or not the client is logged in.
   */
  @CheckReturnValue(when = When.NEVER)
  public synchronized boolean isLoggedIn() {
    return currentUser != null;
  }

  public synchronized StitchUserT getUser() {
    return currentUser;
  }

  /**
   * Performs a request against Stitch using the provided {@link StitchAuthRequest} object, and
   * returns the response.
   *
   * @param stitchReq the request to perform.
   * @return the response to the request, successful or not.
   */
  public Response doAuthenticatedRequest(final StitchAuthRequest stitchReq) {
    try {
      return requestClient.doRequest(prepareAuthRequest(stitchReq));
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
  public <T> Stream<T> openAuthenticatedStream(final StitchRequest stitchReq,
                                               final Decoder<T> decoder) {
    return openAuthenticatedStream(stitchReq, decoder, true);
  }

  private <T> Stream<T> openAuthenticatedStream(final StitchRequest stitchReq,
                                                final Decoder<T> decoder,
                                                final boolean tryRefresh) {
    try {
      return new Stream<>(
          requestClient.doStreamRequest(stitchReq.builder().withPath(
              stitchReq.getPath() + AuthStreamFields.AUTH_TOKEN + getAuthInfo().getAccessToken()
          ).build()),
          decoder
      );
    } catch (final StitchServiceException ex) {
      return handleAuthFailure(ex, stitchReq, decoder, tryRefresh);
    }
  }

  protected synchronized StitchUserT loginWithCredentialInternal(
      final StitchCredential credential) {
    if (!isLoggedIn()) {
      return doLogin(credential, false);
    }

    if (credential.getProviderCapabilities().getReusesExistingSession()
        && credential.getProviderType().equals(currentUser.getLoggedInProviderType())) {
      return currentUser;
    }

    logoutInternal();
    return doLogin(credential, false);
  }

  protected synchronized StitchUserT linkUserWithCredentialInternal(
      final CoreStitchUser user, final StitchCredential credential) {
    if (user != currentUser) {
      throw new StitchClientException(StitchClientErrorCode.USER_NO_LONGER_VALID);
    }

    return doLogin(credential, true);
  }

  protected synchronized void logoutInternal() {
    if (!isLoggedIn()) {
      return;
    }
    try {
      doLogout();
    } catch (final StitchServiceException ex) {
      // Do nothing
    } finally {
      clearAuth();
    }
  }

  protected synchronized boolean hasDeviceId() {
    return authInfo.getDeviceId() != null
        && !authInfo.getDeviceId().isEmpty()
        && !authInfo.getDeviceId().equals("000000000000000000000000");
  }

  protected synchronized String getDeviceId() {
    if (!hasDeviceId()) {
      return null;
    }
    return authInfo.getDeviceId();
  }

  private synchronized StitchRequest prepareAuthRequest(final StitchAuthRequest stitchReq) {
    if (!isLoggedIn()) {
      throw new StitchClientException(StitchClientErrorCode.MUST_AUTHENTICATE_FIRST);
    }

    final StitchRequest.Builder newReq = stitchReq.builder();
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

  private <T> Stream<T> handleAuthFailure(final StitchServiceException ex,
                                          final StitchRequest req,
                                          final Decoder<T> decoder,
                                          final boolean tryRefresh) {
    if (ex.getErrorCode() != StitchServiceErrorCode.INVALID_SESSION) {
      throw ex;
    }

    if (!tryRefresh) {
      clearAuth();
      throw ex;
    }

    tryRefreshAccessToken(req.getStartedAt());

    return openAuthenticatedStream(req, decoder, false);
  }

  private Response handleAuthFailure(final StitchServiceException ex, final StitchAuthRequest req) {
    if (ex.getErrorCode() != StitchServiceErrorCode.INVALID_SESSION) {
      throw ex;
    }

    // using a refresh token implies we cannot refresh anything, so clear auth and
    // notify
    if (req.getUseRefreshToken() || !req.getShouldRefreshOnFailure()) {
      clearAuth();
      throw ex;
    }

    tryRefreshAccessToken(req.getStartedAt());

    return doAuthenticatedRequest(req.builder().withShouldRefreshOnFailure(false).build());
  }

  // use this critical section to create a queue of pending outbound requests
  // that should wait on the result of doing a token refresh or logout. This will
  // prevent too many refreshes happening one after the other.
  private synchronized void tryRefreshAccessToken(final Long reqStartedAt) {
    if (!isLoggedIn()) {
      throw new StitchClientException(StitchClientErrorCode.LOGGED_OUT_DURING_REQUEST);
    }

    try {
      final Jwt jwt = Jwt.fromEncoded(authInfo.getAccessToken());
      if (jwt.getIssuedAt() >= reqStartedAt) {
        return;
      }
    } catch (final IOException e) {
      // Swallow
    }

    // retry
    refreshAccessToken();
  }

  synchronized void refreshAccessToken() {
    final StitchAuthRequest.Builder reqBuilder = new StitchAuthRequest.Builder();
    reqBuilder
        .withRefreshToken()
        .withPath(authRoutes.getSessionRoute())
        .withMethod(Method.POST);

    final Response response = doAuthenticatedRequest(reqBuilder.build());
    try {
      final AuthInfo partialInfo = AuthInfo.readFromApi(response.getBody());
      authInfo = authInfo.merge(partialInfo);
    } catch (final IOException e) {
      throw new StitchRequestException(e, StitchRequestErrorCode.DECODING_ERROR);
    }

    try {
      authInfo.writeToStorage(storage);
    } catch (final IOException e) {
      throw new StitchClientException(StitchClientErrorCode.COULD_NOT_PERSIST_AUTH_INFO);
    }
  }

  private void attachAuthOptions(final Document authBody) {
    final Document options = new Document();
    options.put(AuthLoginFields.DEVICE, getDeviceInfo());
    authBody.put(AuthLoginFields.OPTIONS, options);
  }

  // callers of doLogin should be synchronized before calling in.
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
    final AuthInfo oldInfo = authInfo;
    final StitchUserT oldUser = currentUser;

    newAuthInfo =
        authInfo.merge(
            new AuthInfo(
                newAuthInfo.getUserId(),
                newAuthInfo.getDeviceId(),
                newAuthInfo.getAccessToken(),
                newAuthInfo.getRefreshToken(),
                credential.getProviderType(),
                credential.getProviderName(),
                null));

    // Provisionally set so we can make a profile request
    authInfo = newAuthInfo;
    currentUser =
        getUserFactory()
            .makeUser(
                authInfo.getUserId(),
                credential.getProviderType(),
                credential.getProviderName(),
                null);

    final StitchUserProfileImpl profile;
    try {
      profile = doGetUserProfile();
    } catch (final Exception ex) {
      // If this was a link request, back out of setting authInfo and reset any created user. This
      // will keep the currently logged in user logged in if the profile request failed, and in
      // this particular edge case the user is linked, but they are logged in with their older
      // credentials.
      if (asLinkRequest) {
        authInfo = oldInfo;
        currentUser = oldUser;
      } else { // otherwise if this was a normal login request, log the user out
        clearAuth();
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
      newAuthInfo.writeToStorage(storage);
    } catch (final IOException e) {
      // Back out of setting authInfo
      authInfo = oldInfo;
      currentUser = null;
      throw new StitchClientException(StitchClientErrorCode.COULD_NOT_PERSIST_AUTH_INFO);
    }

    authInfo = newAuthInfo;
    currentUser =
        getUserFactory()
            .makeUser(
                authInfo.getUserId(),
                credential.getProviderType(),
                credential.getProviderName(),
                profile);

    return currentUser;
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

  private void doLogout() {
    final StitchAuthRequest.Builder reqBuilder = new StitchAuthRequest.Builder();
    reqBuilder.withRefreshToken().withPath(authRoutes.getSessionRoute()).withMethod(Method.DELETE);
    doAuthenticatedRequest(reqBuilder.build());
  }

  private synchronized void clearAuth() {
    if (!isLoggedIn()) {
      return;
    }
    authInfo = authInfo.loggedOut();
    try {
      authInfo.writeToStorage(storage);
    } catch (final IOException e) {
      throw new StitchClientException(StitchClientErrorCode.COULD_NOT_PERSIST_AUTH_INFO);
    }
    currentUser = null;
    onAuthEvent();
  }

  /**
   * Closes the component down by stopping the access token refresher.
   */
  public void close() throws IOException {
    if (refresherThread != null) {
      refresherThread.interrupt();
    }
  }

  protected StitchRequestClient getRequestClient() {
    return requestClient;
  }

  protected StitchAuthRoutes getAuthRoutes() {
    return authRoutes;
  }

  private static class AuthStreamFields {
    static final String AUTH_TOKEN = "&stitch_at=";
  }

  private static class AuthLoginFields {
    static final String OPTIONS = "options";
    static final String DEVICE = "device";
  }
}
