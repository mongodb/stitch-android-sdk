package com.mongodb.stitch.core.internal.common;

public final class OperationResult<SuccessType, FailureType> {
  private final SuccessType result;
  private final FailureType failureResult;
  private final boolean isSuccessful;

  private OperationResult(
      final SuccessType result, final FailureType failureResult, final boolean isSuccessful) {
    this.result = result;
    this.failureResult = failureResult;
    this.isSuccessful = isSuccessful;
  }

  public static <T, U> OperationResult<T, U> successfulResultOf(final T value) {
    return new OperationResult<>(value, null, true);
  }

  public static <T, U> OperationResult<T, U> failedResultOf(final U value) {
    return new OperationResult<>(null, value, false);
  }

  public boolean isSuccessful() {
    return isSuccessful;
  }

  public SuccessType getResult() {
    if (!isSuccessful) {
      throw new IllegalStateException("operation was failed, not successful");
    }
    return result;
  }

  public FailureType getFailure() {
    if (isSuccessful) {
      throw new IllegalStateException("operation was successful, not failed");
    }
    return failureResult;
  }
}
