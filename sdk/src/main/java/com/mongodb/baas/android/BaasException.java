package com.mongodb.baas.android;

import static com.mongodb.baas.android.BaasError.ErrorCode;

/**
 * BaasExceptions represent a class of exceptions that happen while
 * utilizing and communicate BaaS.
 */
public class BaasException extends RuntimeException {

    public BaasException() {
        super();
    }

    public BaasException(final Throwable t) {
        super(t);
    }

    public BaasException(final String message, final Throwable t) {
        super(message, t);
    }

    public BaasException(final String message) {
        super(message);
    }

    /**
     * A BaasRequestException is an exception that happens while making a request to BaaS
     * servers.
     */
    public static class BaasRequestException extends BaasException {
        public BaasRequestException(final Throwable t) {
            super(t);
        }

        public BaasRequestException(final String message) {
            super(message);
        }
    }

    /**
     * A BaasServiceException is an exception that happens when the BaaS server has deemed
     * a request as failing for a reason. This exception captures that reason.
     */
    public static class BaasServiceException extends BaasRequestException {

        private final ErrorCode _errorCode;

        public BaasServiceException(final Throwable t) {
            super(t);
            _errorCode = ErrorCode.UNKNOWN;
        }

        public BaasServiceException(final String message) {
            super(message);
            _errorCode = ErrorCode.UNKNOWN;
        }

        public BaasServiceException(final String message, final ErrorCode errorCode) {
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
     * A BaasClientException is an exception that happens locally on the client and is typically
     * a user error.
     */
    public static class BaasClientException extends BaasException {
        public BaasClientException(final Throwable t) {
            super(t);
        }

        public BaasClientException(final String message) {
            super(message);
        }
    }

    /**
     * A BaasAuthException is an exception that happens when trying to authenticate with BaaS.
     */
    static class BaasAuthException extends BaasClientException {

        static final String MUST_AUTH_MESSAGE = "Must first authenticate";

        BaasAuthException(final String message) {
            super(message);
        }
    }
}
