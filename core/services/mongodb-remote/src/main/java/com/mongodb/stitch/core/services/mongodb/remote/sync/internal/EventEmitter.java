package com.mongodb.stitch.core.services.mongodb.remote.sync.internal;

import org.bson.BsonDocument;
import org.bson.BsonValue;

public interface EventEmitter {
  void emitEvent(final BsonValue documentId, final ChangeEvent<BsonDocument> event);
}
