package com.mongodb.stitch.core.services.mongodb.remote.sync;

import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertOneResult;

import org.bson.BsonValue;

public class SyncInsertOneResult extends RemoteInsertOneResult {
  public SyncInsertOneResult(final BsonValue insertedId) {
    super(insertedId);
  }
}
