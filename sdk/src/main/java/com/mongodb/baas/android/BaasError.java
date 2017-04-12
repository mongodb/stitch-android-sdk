package com.mongodb.baas.android;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import static com.mongodb.baas.android.BaasException.BaasRequestException;
import static com.mongodb.baas.android.BaasException.BaasServiceException;

class BaasError {

    /**
     * Parses a network request error, looking for any embedded errors or codes.
     *
     * @param error The network error.
     * @return An exception describing the network error.
     */
    static BaasRequestException parseRequestError(final VolleyError error) {

        if (error.networkResponse == null) {
            return new BaasRequestException(error);
        }

        final String data;
        try {
            data = new String(
                    error.networkResponse.data,
                    HttpHeaderParser.parseCharset(error.networkResponse.headers, "utf-8"));
        } catch (final UnsupportedEncodingException e) {
            throw new BaasRequestException(e);
        }

        final String errorMsg;

        // Look for rich error message
        if (error.networkResponse.headers.containsKey("Content-Type") &&
                error.networkResponse.headers.get("Content-Type").equals("application/json")) {
            try {
                final JSONObject obj = new JSONObject(data);
                errorMsg = obj.getString(Fields.ERROR);
                if (obj.has(Fields.ERROR_CODE)) {
                    final String errorCode = obj.getString(Fields.ERROR_CODE);
                    return new BaasServiceException(errorMsg, ErrorCode.fromCodeName(errorCode));
                }
            } catch (final JSONException e) {
                throw new BaasRequestException(e);
            }
        } else {
            errorMsg = data;
        }

        if (error.networkResponse.statusCode >= 400 && error.networkResponse.statusCode < 600) {
            return new BaasServiceException(errorMsg);
        }

        return new BaasRequestException(error);
    }

    /**
     * ErrorCode represents the set of errors that can come back from a BaaS request.
     */
    public enum ErrorCode {

        /**
         * Session is no longer valid and should be cleared.
         */
        INVALID_SESSION("InvalidSession"),

        /**
         * Generic unknown error.
         */
        UNKNOWN("Unknown");

        private static final Map<String, ErrorCode> codeNameToError = new HashMap<>();

        static {
            codeNameToError.put(INVALID_SESSION._code, INVALID_SESSION);
        }

        private final String _code;

        ErrorCode(final String codeName) {
            _code = codeName;
        }

        public static ErrorCode fromCodeName(final String codeName) {
            if (!codeNameToError.containsKey(codeName)) {
                return UNKNOWN;
            }
            return codeNameToError.get(codeName);
        }
    }

    private static class Fields {
        private static final String ERROR = "error";
        private static final String ERROR_CODE = "errorCode";
    }
}
