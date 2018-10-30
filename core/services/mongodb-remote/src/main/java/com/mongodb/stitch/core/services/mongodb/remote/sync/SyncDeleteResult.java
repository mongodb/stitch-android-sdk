package com.mongodb.stitch.core.services.mongodb.remote.sync;

import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult;

public class SyncDeleteResult extends RemoteDeleteResult {
  public SyncDeleteResult(final long deletedCount) {
    super(deletedCount);
  }
}
