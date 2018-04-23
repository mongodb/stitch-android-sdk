package com.mongodb.stitch.android.services.internal;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.android.services.StitchService;
import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient;
import com.mongodb.stitch.core.services.internal.CoreStitchService;
import com.mongodb.stitch.core.services.internal.StitchServiceRoutes;
import java.util.List;
import java.util.concurrent.Callable;
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecRegistry;

public final class StitchServiceImpl extends CoreStitchService implements StitchService {
  private final TaskDispatcher dispatcher;

  public StitchServiceImpl(
      final StitchAuthRequestClient requestClient,
      final StitchServiceRoutes routes,
      final String name,
      final TaskDispatcher dispatcher) {
    super(requestClient, routes, name);
    this.dispatcher = dispatcher;
  }

  @Override
  public <TResult> Task<TResult> callFunction(final String name, final List<? extends Object> args, final Class<TResult> resultClass) {
    return dispatcher.dispatchTask(
        new Callable<TResult>() {
            @Override
            public TResult call() throws Exception {
                return callFunctionInternal(name, args, resultClass);
            }
        });
    }

  @Override
  public <TResult> Task<TResult> callFunction(final String name, final List<? extends Object> args, final Codec<TResult> resultCodec) {
    return dispatcher.dispatchTask(
        new Callable<TResult>() {
            @Override
            public TResult call() throws Exception {
                return callFunctionInternal(name, args, resultCodec);
            }
        });
    }
}
