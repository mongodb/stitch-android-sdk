package com.mongodb.stitch.core.services.mongodb.remote.sync.internal;

import com.mongodb.MongoNamespace;

import org.bson.BsonValue;

import java.util.Set;

public interface StaleDocumentFetcher {
  Set<BsonValue> getStaleDocumentIds(NamespaceSynchronizationConfig namespace);
}
