package com.mongodb.stitch.server.services.mongodb.remote.internal;

import com.mongodb.stitch.core.services.mongodb.remote.sync.CoreSyncAggregateIterable;
import com.mongodb.stitch.server.services.mongodb.remote.SyncAggregateIterable;

public class SyncAggregateIterableImpl<ResultT>
    extends RemoteMongoIterableImpl<ResultT>
    implements SyncAggregateIterable<ResultT> {
  SyncAggregateIterableImpl(final CoreSyncAggregateIterable<ResultT> iterable) {
    super(iterable);
  }
}
