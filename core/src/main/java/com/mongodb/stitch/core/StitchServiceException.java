package com.mongodb.stitch.core;

/**
 * A StitchServiceException is an exception indicating that an error came from the Stitch server
 * after a request was completed, with an error message and an error code defined in the
 * `StitchServiceErrorCode` enum.
 *
 * It is possible that the error code will be `UNKNOWN`, which can mean one of several
 * possibilities: the Stitch server returned a message that this version of the SDK does not yet
 * recognize, the server is not a Stitch server and returned an unexpected message, or the response
 * was corrupted. In these cases, the associated message will be the plain text body of the
 * response, or an empty string if the body is empty or not decodable as plain text.
 */
public final class StitchServiceException extends StitchException {

  private final StitchServiceErrorCode _errorCode;

  public StitchServiceException(final StitchServiceErrorCode errorCode) {
    super(errorCode.getCodeName());
    _errorCode = errorCode;
  }

  public StitchServiceException(final String message, final StitchServiceErrorCode errorCode) {
    super(message);
    _errorCode = errorCode;
  }

  /**
   * @return The {@link StitchServiceErrorCode} associated with the response to the request.
   */
  public StitchServiceErrorCode getErrorCode() {
    return _errorCode;
  }
}
