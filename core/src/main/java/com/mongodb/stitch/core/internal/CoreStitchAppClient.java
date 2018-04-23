package com.mongodb.stitch.core.internal;

import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient;
import com.mongodb.stitch.core.internal.net.Method;
import com.mongodb.stitch.core.internal.net.StitchAppRoutes;
import com.mongodb.stitch.core.internal.net.StitchAuthDocRequest;
import java.util.Arrays;
import java.util.List;
import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.Decoder;
import org.bson.codecs.configuration.CodecRegistry;

public final class CoreStitchAppClient {
  private final StitchAuthRequestClient authRequestClient;
  private final StitchAppRoutes routes;

  public CoreStitchAppClient(
      final StitchAuthRequestClient authRequestClient,
      final StitchAppRoutes routes
  ) {
    this.authRequestClient = authRequestClient;
    this.routes = routes;
  }

  private StitchAuthDocRequest getCallFunctionRequest(final String name, final List<? extends Object> args) {
    final Document body = new Document();
    body.put("name", name);
    body.put("arguments", args);

    final StitchAuthDocRequest.Builder reqBuilder = new StitchAuthDocRequest.Builder();
    reqBuilder.withMethod(Method.POST).withPath(routes.getFunctionCallRoute());
    reqBuilder.withDocument(body);
    return reqBuilder.build();
  }

  /**
   * Calls the specified Stitch function, and decodes the response into a value using the provided
   * {@link Decoder}.
   *
   * @param name The name of the Stitch function to call.
   * @param args The arguments to pass to the Stitch function.
   * @param decoder The {@link Decoder} to use to decode the Stitch response into a value.
   * @param <T> The type into which the Stitch response will be decoded.
   * @return The decoded value.
   */
  public <T> T callFunctionInternal(final String name, final List<? extends Object> args, Decoder<T> decoder) {
    return authRequestClient.doAuthenticatedJSONRequest(getCallFunctionRequest(name, args), decoder);
  }

  /**
   * Calls the specified Stitch function, and decodes the response into an instance of the
   * specified type. The response will be decoded using the codec registry specified when the
   * client was configured. If no codec registry was configured, a default codec registry will be
   * used. The default codec registry supports the mappings specified
   * <a href="http://mongodb.github.io/mongo-java-driver/3.1/bson/documents/#document">here</a>
   *
   * @param name The name of the Stitch function to call.
   * @param args The arguments to pass to the Stitch function.
   * @param resultClass The class that the Stitch response should be decoded as.
   * @param <T> The type into which the Stitch response will be decoded.
   * @return The decoded value.
   */
  public <T> T callFunctionInternal(final String name, final List<? extends Object> args, Class<T> resultClass) {
    return authRequestClient.doAuthenticatedJSONRequest(getCallFunctionRequest(name, args), resultClass);
  }
}
