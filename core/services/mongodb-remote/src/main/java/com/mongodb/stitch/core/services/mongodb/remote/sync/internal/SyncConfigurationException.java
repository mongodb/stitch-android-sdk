package com.mongodb.stitch.core.services.mongodb.remote.sync.internal;

/**
 * Exception pertaining to any errors pertaining to SyncConfiguration
 */
public class SyncConfigurationException extends RuntimeException {
  SyncConfigurationException(final String msg) {
    super(msg);
  }
}