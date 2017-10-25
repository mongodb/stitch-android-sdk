package com.mongodb.stitch.android;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static com.mongodb.stitch.android.http.ContentTypes.APPLICATION_JSON;
import static com.mongodb.stitch.android.http.Headers.CONTENT_TYPE;

class StitchError {

    /**
     * Parses a network request error, looking for any embedded errors or codes.
     *
     * @param error The network error.
     * @return An exception describing the network error.
     */
    static StitchException.StitchRequestException parseRequestError(final VolleyError error) {

        if (error.networkResponse == null) {
            return new StitchException.StitchRequestException(error);
        }

        final String data;
        try {
            data = new String(
                    error.networkResponse.data,
                    HttpHeaderParser.parseCharset(
                            error.networkResponse.headers,
                            StandardCharsets.UTF_8.displayName())
                    );
        } catch (final UnsupportedEncodingException e) {
            throw new StitchException.StitchRequestException(e);
        }

        final String errorMsg;

        // Look for rich error message
        if (error.networkResponse.headers.containsKey(CONTENT_TYPE) &&
                error.networkResponse.headers.get(CONTENT_TYPE).equals(APPLICATION_JSON)) {
            try {
                final JSONObject obj = new JSONObject(data);
                errorMsg = obj.getString(Fields.ERROR);
                if (obj.has(Fields.ERROR_CODE)) {
                    final String errorCode = obj.getString(Fields.ERROR_CODE);
                    return new StitchException.StitchServiceException(errorMsg, ErrorCode.fromCodeName(errorCode));
                }
            } catch (final JSONException e) {
                throw new StitchException.StitchRequestException(e);
            }
        } else {
            errorMsg = data;
        }

        if (error.networkResponse.statusCode >= 400 && error.networkResponse.statusCode < 600) {
            return new StitchException.StitchServiceException(errorMsg);
        }

        return new StitchException.StitchRequestException(error);
    }

    /**
     * ErrorCode represents the set of errors that can come back from a Stitch request.
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
