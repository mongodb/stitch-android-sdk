package com.mongodb.baas.sdk;

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

    public static class BaasRequestException extends BaasException {
        public BaasRequestException(final Throwable t) {
            super(t);
        }

        public BaasRequestException(final String message) {
            super(message);
        }
    }

    public static class BaasClientException extends BaasRequestException {
        public BaasClientException(final Throwable t) {
            super(t);
        }

        public BaasClientException(final String message) {
            super(message);
        }
    }

    public static class BaasServiceException extends BaasClientException {

        private final ErrorCode _errorCode;

        public enum ErrorCode {
            BAD_SESSION,
            UNKNOWN
        }

        public BaasServiceException(final Throwable t) {
            super(t);
            _errorCode = ErrorCode.UNKNOWN;
        }

        public BaasServiceException(final String message) {
            super(message);
            _errorCode = ErrorCode.UNKNOWN;;
        }

        public BaasServiceException(final String message, final ErrorCode errorCode) {
            super(message);
            _errorCode = errorCode;
        }

        public ErrorCode getErrorCode() {
            return _errorCode;
        }
    }

    public static class BaasAuthException extends BaasClientException {
        public BaasAuthException(final String message) {
            super(message);
        }
    }
}
