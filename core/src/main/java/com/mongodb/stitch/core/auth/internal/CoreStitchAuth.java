package com.mongodb.stitch.core.auth.internal;

import com.mongodb.stitch.core.StitchClientErrorCode;
import com.mongodb.stitch.core.StitchClientException;
import com.mongodb.stitch.core.StitchRequestErrorCode;
import com.mongodb.stitch.core.StitchRequestException;
import com.mongodb.stitch.core.StitchServiceErrorCode;
import com.mongodb.stitch.core.StitchServiceException;
import com.mongodb.stitch.core.auth.StitchCredential;
import com.mongodb.stitch.core.auth.internal.models.APICoreUserProfile;
import com.mongodb.stitch.core.internal.common.BSONUtils;
import com.mongodb.stitch.core.internal.common.IOUtils;
import com.mongodb.stitch.core.internal.common.StitchObjectMapper;
import com.mongodb.stitch.core.internal.common.Storage;
import com.mongodb.stitch.core.internal.net.ContentTypes;
import com.mongodb.stitch.core.internal.net.Headers;
import com.mongodb.stitch.core.internal.net.Method;
import com.mongodb.stitch.core.internal.net.Response;
import com.mongodb.stitch.core.internal.net.StitchAuthDocRequest;
import com.mongodb.stitch.core.internal.net.StitchAuthRequest;
import com.mongodb.stitch.core.internal.net.StitchDocRequest;
import com.mongodb.stitch.core.internal.net.StitchRequest;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;
import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.Decoder;
import org.bson.codecs.configuration.CodecRegistry;

/**
 * synchronization in this class happens around the authInfo and currentUser objects such that
 * access to them is 1. always atomic and 2. queued to prevent excess token refreshes.
 *
 * @param <TStitchUser>
 */
public abstract class CoreStitchAuth<TStitchUser extends CoreStitchUser> implements StitchAuthRequestClient,
    Closeable {

  protected final StitchRequestClient requestClient;
  protected final StitchAuthRoutes authRoutes;
  private final Storage storage;
  private final Thread refresherThread;
  private AuthInfo authInfo;
  private TStitchUser currentUser;
  private CodecRegistry configuredCustomCodecRegistry; // is valid to be null

  protected CoreStitchAuth(
      final StitchRequestClient requestClient,
      final StitchAuthRoutes authRoutes,
      final Storage storage,
      final CodecRegistry configuredCustomCodecRegistry) {
    this.requestClient = requestClient;
    this.authRoutes = authRoutes;
    this.storage = storage;
    this.configuredCustomCodecRegistry = configuredCustomCodecRegistry;

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

    if (this.authInfo.userId != null) {
      // this implies other properties we are interested should be set
      this.currentUser =
          getUserFactory()
              .makeUser(
                  this.authInfo.userId,
                  this.authInfo.loggedInProviderType,
                  this.authInfo.loggedInProviderName,
                  this.authInfo.userProfile);
    }

    refresherThread = new Thread(new AccessTokenRefresher<>(new WeakReference<>(this)), AccessTokenRefresher.class.getSimpleName());
    refresherThread.start();
  }

  protected abstract StitchUserFactory<TStitchUser> getUserFactory();
  protected abstract void onAuthEvent();
  protected abstract Document getDeviceInfo();

  synchronized AuthInfo getAuthInfo() {
    return authInfo;
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  public synchronized boolean isLoggedIn() {
    return currentUser != null;
  }

  public synchronized TStitchUser getUser() {
    return currentUser;
  }

  public Response doAuthenticatedRequest(final StitchAuthRequest stitchReq) {
    try {
      return requestClient.doRequest(prepareAuthRequest(stitchReq));
    } catch (final StitchServiceException ex) {
      return handleAuthFailure(ex, stitchReq);
    }
  }

  /**
   * Performs a request against a server using the provided {@link StitchAuthDocRequest} object,
   * and decodes the JSON body of the response into a T value using the provided codec.
   *
   * @param stitchReq The request to perform.
   * @param decoder The {@link Decoder} to use to decode the JSON of the response into a T value.
   * @param <T> The type into which the JSON response will be decoded.
   * @return The decoded value.
   */
  public <T> T doAuthenticatedJSONRequest(final StitchAuthDocRequest stitchReq, Decoder<T> decoder) {
    final Response response = doAuthenticatedJSONRequestRaw(stitchReq);

    try {
      return BSONUtils.parseValue(IOUtils.readAllToString(response.body), decoder);
    } catch (final Exception e) {
      throw new StitchRequestException(e, StitchRequestErrorCode.DECODING_ERROR);
    }
  }

  /**
   * Performs a request against a server using the provided {@link StitchAuthDocRequest} object,
   * and decodes the JSON body of the response into a T value as specified by the provided class
   * type. The type will be decoded using the codec found for T in the codec registry configured
   * for this {@link CoreStitchAuth}. If there is no codec registry configured for this auth
   * instance, a default codec registry will be used. If the provided type is not supported by the
   * codec registry to be used, the method will throw a
   * {@link org.bson.codecs.configuration.CodecConfigurationException}.
   *
   * @param stitchReq The request to perform.
   * @param resultClass The class that the JSON response should be decoded as.
   * @param <T> The type into which the JSON response will be decoded into.
   * @return The decoded value.
   */
  public <T> T doAuthenticatedJSONRequest(final StitchAuthDocRequest stitchReq, Class<T> resultClass) {
    final Response response = doAuthenticatedJSONRequestRaw(stitchReq);

    try {
      if(this.configuredCustomCodecRegistry != null) {
        return BSONUtils.parseValue(
                IOUtils.readAllToString(response.body),
                resultClass,
                this.configuredCustomCodecRegistry);
      } else {
        return BSONUtils.parseValue(IOUtils.readAllToString(response.body), resultClass);
      }

    } catch (final Exception e) {
      throw new StitchRequestException(e, StitchRequestErrorCode.DECODING_ERROR);
    }
  }

  public Response doAuthenticatedJSONRequestRaw(final StitchAuthDocRequest stitchReq) {
    final StitchAuthDocRequest.Builder newReqBuilder = stitchReq.builder();
    newReqBuilder.withBody(stitchReq.document.toJson().getBytes(StandardCharsets.UTF_8));
    final Map<String, String> newHeaders = newReqBuilder.getHeaders(); // This is not a copy
    newHeaders.put(Headers.CONTENT_TYPE, ContentTypes.APPLICATION_JSON);
    newReqBuilder.withHeaders(newHeaders);

    return doAuthenticatedRequest(newReqBuilder.build());
  }

  protected synchronized TStitchUser loginWithCredentialBlocking(final StitchCredential credential) {
    if (!isLoggedIn()) {
      return doLogin(credential, false);
    }

    if (credential.getProviderCapabilities().reusesExistingSession) {
      if (credential.getProviderType().equals(currentUser.getLoggedInProviderType())) {
        return currentUser;
      }
    }

    logoutBlocking();
    return doLogin(credential, false);
  }

  protected synchronized TStitchUser linkUserWithCredentialBlocking(
      final CoreStitchUser user, final StitchCredential credential) {
    if (user != currentUser) {
      throw new StitchClientException(StitchClientErrorCode.USER_NO_LONGER_VALID);
    }

    return doLogin(credential, true);
  }

  protected synchronized void logoutBlocking() {
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

  protected boolean hasDeviceId() {
    return authInfo.deviceId != null
        && !authInfo.deviceId.isEmpty()
        && !authInfo.deviceId.equals("000000000000000000000000");
  }

  protected String getDeviceId() {
    if (!hasDeviceId()) {
      return null;
    }
    return authInfo.deviceId;
  }

  private synchronized StitchRequest prepareAuthRequest(final StitchAuthRequest stitchReq) {
    if (!isLoggedIn()) {
      throw new StitchClientException(StitchClientErrorCode.MUST_AUTHENTICATE_FIRST);
    }

    final StitchRequest.Builder newReq = stitchReq.builder();
    final Map<String, String> newHeaders = newReq.getHeaders(); // This is not a copy
    if (stitchReq.useRefreshToken) {
      newHeaders.put(Headers.AUTHORIZATION, Headers.getAuthorizationBearer(authInfo.refreshToken));
    } else {
      newHeaders.put(Headers.AUTHORIZATION, Headers.getAuthorizationBearer(authInfo.accessToken));
    }
    newReq.withHeaders(newHeaders);
    return newReq.build();
  }

  private Response handleAuthFailure(final StitchServiceException ex, final StitchAuthRequest req) {
    if (ex.getErrorCode() != StitchServiceErrorCode.INVALID_SESSION) {
      throw ex;
    }

    // using a refresh token implies we cannot refresh anything, so clear auth and
    // notify
    if (req.useRefreshToken) {
      clearAuth();
      throw ex;
    }

    tryRefreshAccessToken(req.startedAt);

    return doAuthenticatedRequest(req);
  }

  // use this critical section to create a queue of pending outbound requests
  // that should wait on the result of doing a token refresh or logout. This will
  // prevent too many refreshes happening one after the other.
  private synchronized void tryRefreshAccessToken(final Long reqStartedAt) {
    if (!isLoggedIn()) {
      throw new StitchClientException(StitchClientErrorCode.LOGGED_OUT_DURING_REQUEST);
    }

    try {
      final JWT jwt = JWT.fromEncoded(authInfo.accessToken);
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
    reqBuilder.withRefreshToken().withPath(authRoutes.getSessionRoute()).withMethod(Method.POST);

    final Response response = doAuthenticatedRequest(reqBuilder.build());
    try {
      final AuthInfo partialInfo = AuthInfo.readFromAPI(response.body);
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
  private TStitchUser doLogin(final StitchCredential credential, final boolean asLinkRequest) {
    final Response response = doLoginRequest(credential, asLinkRequest);
    final TStitchUser user = processLoginResponse(credential, response);
    if (asLinkRequest) {
      onAuthEvent();
    } else {
      onAuthEvent();
    }
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
      return requestClient.doJSONRequestRaw(reqBuilder.build());
    }
    final StitchAuthDocRequest linkRequest =
        new StitchAuthDocRequest(reqBuilder.build(), reqBuilder.getDocument());
    return doAuthenticatedJSONRequestRaw(linkRequest);
  }

  private TStitchUser processLoginResponse(final StitchCredential credential, final Response response) {
    AuthInfo newAuthInfo;
    try {
      newAuthInfo = AuthInfo.readFromAPI(response.body);
    } catch (final IOException e) {
      throw new StitchRequestException(e, StitchRequestErrorCode.DECODING_ERROR);
    }

    newAuthInfo =
        authInfo.merge(
            new AuthInfo(
                newAuthInfo.userId,
                newAuthInfo.deviceId,
                newAuthInfo.accessToken,
                newAuthInfo.refreshToken,
                credential.getProviderType(),
                credential.getProviderName(),
                null));

    // Provisionally set so we can make a profile request
    final AuthInfo oldInfo = authInfo;
    authInfo = newAuthInfo;
    currentUser =
        getUserFactory()
            .makeUser(
                authInfo.userId, credential.getProviderType(), credential.getProviderName(), null);

    final StitchUserProfileImpl profile;
    try {
      profile = doGetUserProfile();
    } catch (final Exception ex) {
      // Back out of setting authInfo
      authInfo = oldInfo;
      currentUser = null;
      throw ex;
    }

    // Finally set the info and user
    newAuthInfo =
        newAuthInfo.merge(
            new AuthInfo(
                newAuthInfo.userId,
                newAuthInfo.deviceId,
                newAuthInfo.accessToken,
                newAuthInfo.refreshToken,
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
                authInfo.userId,
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
      return StitchObjectMapper.getInstance().readValue(response.body, APICoreUserProfile.class);
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

  public void close() throws IOException {
    refresherThread.interrupt();
  }

  private static class AuthLoginFields {
    static final String OPTIONS = "options";
    static final String DEVICE = "device";
  }
}
