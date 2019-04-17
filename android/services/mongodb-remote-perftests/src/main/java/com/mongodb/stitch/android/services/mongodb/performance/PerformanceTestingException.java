package com.mongodb.stitch.android.services.mongodb.performance;

/**
 * Exception pertaining to any errors related to the
 * Performance Testing module.
 */
class PerformanceTestingException extends Exception {
  PerformanceTestingException(final String msg) {
    super(msg);
  }
}