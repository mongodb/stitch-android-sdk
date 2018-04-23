package com.mongodb.stitch.core;

/**
 * StitchExceptions represent a class of exceptions that happen while utilizing the Stitch SDK and
 * communicating Stitch. Most Stitch exception types will inherit from this base class.
 */
public class StitchException extends RuntimeException {
  StitchException() {
    super();
  }

  StitchException(final Exception exception) { super(exception); }

  StitchException(final String message) {
    super(message);
  }
}
