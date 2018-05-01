package com.mongodb.stitch.core.services.internal;

import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient;
import com.mongodb.stitch.core.internal.net.Method;
import com.mongodb.stitch.core.internal.net.StitchAuthDocRequest;
import java.util.Arrays;
import java.util.List;
import org.bson.Document;
import org.bson.codecs.Codec;

public class CoreStitchService {
  private final StitchAuthRequestClient requestClient;
  private final StitchServiceRoutes serviceRoutes;
  private final String serviceName;

  protected CoreStitchService(
      final StitchAuthRequestClient requestClient,
      final StitchServiceRoutes routes,
      final String name) {
    this.requestClient = requestClient;
    this.serviceRoutes = routes;
    this.serviceName = name;
  }

  private StitchAuthDocRequest getCallServiceFunctionRequest(final String name, final List<? extends Object> args) {
    final Document body = new Document();
    body.put("name", name);
    body.put("service", serviceName);
    body.put("arguments", args);

    final StitchAuthDocRequest.Builder reqBuilder = new StitchAuthDocRequest.Builder();
    reqBuilder.withMethod(Method.POST).withPath(serviceRoutes.getFunctionCallRoute());
    reqBuilder.withDocument(body);
    return reqBuilder.build();
  }

  public <T> T callFunctionInternal(final String name, final List<? extends Object> args, Codec<T> codec) {
    return requestClient.doAuthenticatedJSONRequest(getCallServiceFunctionRequest(name, args), codec);
  }

  public <T> T callFunctionInternal(final String name, final List<? extends Object> args, Class<T> resultClass) {
    return requestClient.doAuthenticatedJSONRequest(getCallServiceFunctionRequest(name, args), resultClass);
  }
}
