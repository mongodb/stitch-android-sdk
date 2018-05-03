package com.mongodb.stitch.core;

/**
 * StitchRequestErrorCode represents the reasons that a request may fail.
 */
public enum StitchRequestErrorCode {
    TRANSPORT_ERROR,
    TRANSPORT_TIMEOUT_ERROR,
    DECODING_ERROR
}
