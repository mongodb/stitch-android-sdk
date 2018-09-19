package com.mongodb.stitch.core.services.mongodb.remote.sync;

import org.bson.BsonValue;

/**
 * ErrorListener receives non-network related errors that occur.
 */
public interface ErrorListener {

  /**
   * Called when an error happens for the given document id.
   *
   * @param documentId the _id of the document related to the error.
   * @param error the error.
   */
  void onError(final BsonValue documentId, final Exception error);
}
