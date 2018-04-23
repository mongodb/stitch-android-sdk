package com.mongodb.stitch.core.auth.providers.userpass;

import com.mongodb.stitch.core.auth.internal.StitchAuthRoutes;
import com.mongodb.stitch.core.auth.providers.CoreAuthProviderClient;
import com.mongodb.stitch.core.internal.net.Method;
import com.mongodb.stitch.core.internal.net.StitchDocRequest;
import com.mongodb.stitch.core.internal.net.StitchRequestClient;
import org.bson.Document;

public abstract class CoreUserPasswordAuthProviderClient extends CoreAuthProviderClient {

  public static final String DEFAULT_PROVIDER_NAME = "local-userpass";
  static final String PROVIDER_TYPE = "local-userpass";
  private final Routes routes;

  protected CoreUserPasswordAuthProviderClient(
      final String providerName,
      final StitchRequestClient requestClient,
      final StitchAuthRoutes routes) {
    super(providerName, requestClient, routes);
    this.routes = new Routes(this.authRoutes, providerName);
  }

  public UserPasswordCredential getCredential(final String username, final String password) {
    return new UserPasswordCredential(providerName, username, password);
  }

  protected void registerWithEmailInternal(final String email, final String password) {
    final StitchDocRequest.Builder reqBuilder = new StitchDocRequest.Builder();
    reqBuilder.withMethod(Method.POST).withPath(routes.getRegisterWithEmailRoute());
    reqBuilder.withDocument(
        new Document(RegistrationFields.EMAIL, email)
            .append(RegistrationFields.PASSWORD, password));
    requestClient.doJSONRequestRaw(reqBuilder.build());
  }

  protected void confirmUserInternal(final String token, final String tokenId) {
    final StitchDocRequest.Builder reqBuilder = new StitchDocRequest.Builder();
    reqBuilder.withMethod(Method.POST).withPath(routes.getConfirmUserRoute());
    reqBuilder.withDocument(
        new Document(ActionFields.TOKEN, token).append(ActionFields.TOKEN_ID, tokenId));
    requestClient.doJSONRequestRaw(reqBuilder.build());
  }

  protected void resendConfirmationEmailInternal(final String email) {
    final StitchDocRequest.Builder reqBuilder = new StitchDocRequest.Builder();
    reqBuilder.withMethod(Method.POST).withPath(routes.getResendConfirmationEmailRoute());
    reqBuilder.withDocument(new Document(ActionFields.EMAIL, email));
    requestClient.doJSONRequestRaw(reqBuilder.build());
  }

  protected void resetPasswordInternal(
      final String token, final String tokenId, final String password) {
    final StitchDocRequest.Builder reqBuilder = new StitchDocRequest.Builder();
    reqBuilder.withMethod(Method.POST).withPath(routes.getResetPasswordRoute());
    reqBuilder.withDocument(
        new Document(ActionFields.TOKEN, token)
            .append(ActionFields.TOKEN_ID, tokenId)
            .append(ActionFields.PASSWORD, password));
    requestClient.doJSONRequestRaw(reqBuilder.build());
  }

  protected void sendResetPasswordEmailInternal(final String email) {
    final StitchDocRequest.Builder reqBuilder = new StitchDocRequest.Builder();
    reqBuilder.withMethod(Method.POST).withPath(routes.getSendResetPasswordEmailRoute());
    reqBuilder.withDocument(new Document(ActionFields.EMAIL, email));
    requestClient.doJSONRequestRaw(reqBuilder.build());
  }

  private static class Routes {
    private final StitchAuthRoutes authRoutes;
    private final String providerName;

    public Routes(final StitchAuthRoutes authRoutes, final String providerName) {
      this.authRoutes = authRoutes;
      this.providerName = providerName;
    }

    private String getExtensionRoute(final String path) {
      return String.format("%s/%s", authRoutes.getAuthProviderRoute(providerName), path);
    }

    private String getRegisterWithEmailRoute() {
      return getExtensionRoute("register");
    }

    private String getConfirmUserRoute() {
      return getExtensionRoute("confirm");
    }

    private String getResendConfirmationEmailRoute() {
      return getExtensionRoute("confirm/send");
    }

    private String getResetPasswordRoute() {
      return getExtensionRoute("reset");
    }

    private String getSendResetPasswordEmailRoute() {
      return getExtensionRoute("reset/send");
    }
  }

  private static class RegistrationFields {
    static final String EMAIL = "email";
    static final String PASSWORD = "password";
  }

  private static class ActionFields {
    static final String EMAIL = "email";
    static final String PASSWORD = "password";
    static final String TOKEN = "token";
    static final String TOKEN_ID = "tokenId";
  }
}
