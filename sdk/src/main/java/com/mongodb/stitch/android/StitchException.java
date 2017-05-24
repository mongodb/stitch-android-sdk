package com.mongodb.stitch.android;

import static com.mongodb.stitch.android.StitchError.ErrorCode;

/**
 * StitchExceptions represent a class of exceptions that happen while
 * utilizing and communicate Stitch.
 */
public class StitchException extends RuntimeException {

    public StitchException() {
        super();
    }

    public StitchException(final Throwable t) {
        super(t);
    }

    public StitchException(final String message, final Throwable t) {
        super(message, t);
    }

    public StitchException(final String message) {
        super(message);
    }

    /**
     * A StitchRequestException is an exception that happens while making a request to Stitch
     * servers.
     */
    public static class StitchRequestException extends StitchException {
        public StitchRequestException(final Throwable t) {
            super(t);
        }

        public StitchRequestException(final String message) {
            super(message);
        }
    }

    /**
     * A StitchServiceException is an exception that happens when the Stitch server has deemed
     * a request as failing for a reason. This exception captures that reason.
     */
    public static class StitchServiceException extends StitchRequestException {

        private final ErrorCode _errorCode;

        public StitchServiceException(final Throwable t) {
            super(t);
            _errorCode = ErrorCode.UNKNOWN;
        }

        public StitchServiceException(final String message) {
            super(message);
            _errorCode = ErrorCode.UNKNOWN;
        }

        public StitchServiceException(final String message, final ErrorCode errorCode) {
            super(message);
            _errorCode = errorCode;
        }

        /**
         * @return The {@link ErrorCode} associated with the request.
         */
        public ErrorCode getErrorCode() {
            return _errorCode;
        }
    }

    /**
     * A StitchClientException is an exception that happens locally on the client and is typically
     * a user error.
     */
    public static class StitchClientException extends StitchException {
        public StitchClientException(final Throwable t) {
            super(t);
        }

        public StitchClientException(final String message) {
            super(message);
        }
    }

    /**
     * A StitchAuthException is an exception that happens when trying to authenticate with Stitch.
     */
    static class StitchAuthException extends StitchClientException {

        StitchAuthException(final String message) {
            super(message);
        }
    }
}
