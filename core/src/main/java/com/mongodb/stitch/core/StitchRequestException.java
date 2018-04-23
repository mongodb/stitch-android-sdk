package com.mongodb.stitch.core;

/**
 * Indicates that an error occurred while a request was being carried out. This could be due to
 * (but is not limited to) an unreachable server, a connection timeout, or an inability to decode
 * the result. An error code is included, which indicates whether the error was a transport error
 * or decoding error. The inherited getCause() method from the {@link Exception} class can be used to
 * produce that underlying error that caused a StitchRequestException. In the case of transport
 * errors, these exception are thrown by the underlying
 * {@link com.mongodb.stitch.core.internal.net.Transport} of the Stitch client. An error in
 * decoding the result from the server is typically a {@link java.io.IOException} thrown internally
 * by the Stitch SDK.
 */
public class StitchRequestException extends StitchException {
  private final StitchRequestErrorCode _errorCode;

  public StitchRequestException(final Exception underlyingTransportException, final StitchRequestErrorCode errorCode) {
    super(underlyingTransportException);
    _errorCode = errorCode;
  }

  /**
   * @return The {@link StitchRequestErrorCode} indicating the reason for this exception.
   */
  public StitchRequestErrorCode getErrorCode() {
    return _errorCode;
  }
}
