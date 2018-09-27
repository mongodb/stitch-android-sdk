package com.mongodb.stitch.core.services.mongodb.remote.sync.internal;

import org.bson.BsonValue;

public interface ErrorEmitter {
  void emitError(final BsonValue docId, final String msg);

  void emitError(final BsonValue docId, final String msg, final Exception ex);
}
