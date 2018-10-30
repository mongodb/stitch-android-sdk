package com.mongodb.stitch.android.services.mongodb.remote.internal;

import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.android.services.mongodb.remote.SyncAggregateIterable;
import com.mongodb.stitch.core.services.mongodb.remote.sync.CoreSyncAggregateIterable;

class SyncAggregateIterableImpl<ResultT>
    extends RemoteMongoIterableImpl<ResultT>
    implements SyncAggregateIterable<ResultT> {
  SyncAggregateIterableImpl(
      final CoreSyncAggregateIterable<ResultT> iterable,
      TaskDispatcher dispatcher
  ) {
    super(iterable, dispatcher);
  }
}
