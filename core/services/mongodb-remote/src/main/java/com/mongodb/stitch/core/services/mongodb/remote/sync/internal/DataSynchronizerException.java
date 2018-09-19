package com.mongodb.stitch.core.services.mongodb.remote.sync.internal;

/**
 * Exception pertaining to any errors related to the
 * DataSynchronizer class.
 */
public class DataSynchronizerException extends Exception {
  DataSynchronizerException(String msg) {
    super(msg);
  }
}
