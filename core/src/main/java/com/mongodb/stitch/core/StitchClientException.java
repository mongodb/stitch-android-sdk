package com.mongodb.stitch.core;

/**
 * An exception indicating that an error occurred when using the Stitch client, typically before
 * the client performed a request. An error code indicating the reason for the error is included.
 */
public final class StitchClientException extends StitchException {
  private final StitchClientErrorCode _errorCode;

  public StitchClientException(final StitchClientErrorCode errorCode) {
    _errorCode = errorCode;
  }

  /**
   * @return The {@link StitchClientErrorCode} associated with the request.
   */
  public StitchClientErrorCode getErrorCode() {
    return _errorCode;
  }
}
