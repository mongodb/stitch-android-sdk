package com.mongodb.stitch.core.internal.common;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Dispatcher implements Closeable {
  private final ExecutorService executorService;

  protected Dispatcher() {
    executorService =
        new ThreadPoolExecutor(
            8,
            32,
            60,
            TimeUnit.SECONDS,
            new LinkedBlockingDeque<Runnable>(),
            Executors.defaultThreadFactory());
  }

  protected <T, U> U dispatch(
      final Callable<T> callable, final CallbackAsyncAdapter<T, Exception, U> callbackAdapter) {
    dispatch(callable, (Callback<T, Exception>) callbackAdapter);
    return callbackAdapter.getAdapter();
  }

  private <T> void dispatch(final Callable<T> callable, final Callback<T, Exception> callback) {
    executorService.submit(
        new Runnable() {
          @Override
          public void run() {
            try {
              callback.onComplete(
                  OperationResult.<T, Exception>successfulResultOf(callable.call()));
            } catch (final Exception e) {
              callback.onComplete(OperationResult.<T, Exception>failedResultOf(e));
            }
          }
        });
  }

  @Override
  public void close() throws IOException {
    executorService.shutdownNow();
  }
}
