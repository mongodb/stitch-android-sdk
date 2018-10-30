package com.mongodb.stitch.core.services.mongodb.remote.sync;

import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult;

import org.bson.BsonValue;

public class SyncUpdateResult extends RemoteUpdateResult {
  public SyncUpdateResult(
      final long matchedCount,
      final long modifiedCount,
      final BsonValue upsertedId
  ) {
    super(matchedCount, modifiedCount, upsertedId);
  }
}
