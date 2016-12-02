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

    public static class BaasServerException extends BaasRequestException {
        public BaasServerException(final Throwable t) {
            super(t);
        }

        public BaasServerException(final String message) {
            super(message);
        }
    }

    public static class BaasAuthException extends BaasException {
        public BaasAuthException(final String message) {
            super(message);
        }
    }
}
