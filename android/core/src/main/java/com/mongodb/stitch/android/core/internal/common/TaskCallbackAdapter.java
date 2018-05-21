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
      taskCompletionSource.setResult(result.geResult());
    } else {
      taskCompletionSource.setException(result.getFailure());
    }
  }
}
