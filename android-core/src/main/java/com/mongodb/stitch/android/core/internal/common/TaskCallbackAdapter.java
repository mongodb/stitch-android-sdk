package com.mongodb.stitch.android.core.internal.common;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.mongodb.stitch.core.internal.common.CallbackAsyncAdapter;
import com.mongodb.stitch.core.internal.common.OperationResult;

public final class TaskCallbackAdapter<T> implements CallbackAsyncAdapter<T, Exception, Task<T>> {
  private final TaskCompletionSource<T> taskCompletionSource;

  TaskCallbackAdapter() {
    this.taskCompletionSource = new TaskCompletionSource<>();
  }

  @Override
  public Task<T> getAdapter() {
    return taskCompletionSource.getTask();
  }

  @Override
  public void onComplete(final OperationResult<T, Exception> result) {
    if (result.isSuccessful()) {
      taskCompletionSource.setResult(result.getResult());
    } else {
      taskCompletionSource.setException(result.getFailure());
    }
  }
}
