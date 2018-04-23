package com.mongodb.stitch.server.core.services.internal;

import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient;
import com.mongodb.stitch.core.services.internal.CoreStitchService;
import com.mongodb.stitch.core.services.internal.StitchServiceRoutes;
import com.mongodb.stitch.server.core.services.StitchService;

import org.bson.codecs.Codec;

import java.util.List;

public final class StitchServiceImpl extends CoreStitchService implements StitchService {

  public StitchServiceImpl(
      final StitchAuthRequestClient requestClient,
      final StitchServiceRoutes routes,
      final String name) {
    super(requestClient, routes, name);
  }

  @Override
  public <TResult> TResult callFunction(final String name, final List<? extends Object> args, final Class<TResult> resultClass) {
    return callFunctionInternal(name, args, resultClass);
  }

  @Override
  public <TResult> TResult callFunction(final String name, final List<? extends Object> args, final Codec<TResult> resultCodec) {
    return callFunctionInternal(name, args, resultCodec);
  }
}
