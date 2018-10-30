package com.mongodb.stitch.core.services.mongodb.remote.sync;

import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertManyResult;

import org.bson.BsonValue;

import java.util.Map;

public class SyncInsertManyResult extends RemoteInsertManyResult {
  public SyncInsertManyResult(final Map<Long, BsonValue> insertedIds) {
    super(insertedIds);
  }
}
