package com.mongodb.stitch.core.internal.common;

public interface Callback<T, U> {
  void onComplete(final OperationResult<T, U> result);
}
