package com.mongodb.stitch.android.core.internal.common;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.core.internal.common.Dispatcher;
import java.util.concurrent.Callable;

public final class TaskDispatcher extends Dispatcher {
  public <T> Task<T> dispatchTask(final Callable<T> callable) {
    return dispatch(callable, new TaskCallbackAdapter<T>());
  }
}
