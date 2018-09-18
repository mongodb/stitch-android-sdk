package com.mongodb.stitch.android.services.mongodb.remote.internal;

import android.support.annotation.Nullable;

import com.mongodb.stitch.android.core.internal.common.TaskDispatcher;
import com.mongodb.stitch.android.services.mongodb.remote.SyncFindIterable;
import com.mongodb.stitch.core.services.mongodb.remote.sync.CoreSyncFindIterable;

import org.bson.conversions.Bson;

public class SyncFindIterableImpl<ResultT>
  extends RemoteMongoIterableImpl<ResultT>
  implements SyncFindIterable<ResultT> {

  private final CoreSyncFindIterable<ResultT> proxy;

  SyncFindIterableImpl(
    final CoreSyncFindIterable<ResultT> iterable,
    final TaskDispatcher dispatcher
  ) {
    super(iterable, dispatcher);
    this.proxy = iterable;
  }

  @Override
  public SyncFindIterable<ResultT> filter(@Nullable final Bson filter) {
    proxy.filter(filter);
    return this;
  }

  @Override
  public SyncFindIterable<ResultT> limit(final int limit) {
    proxy.limit(limit);
    return this;
  }

  @Override
  public SyncFindIterable<ResultT> projection(@Nullable final Bson projection) {
    proxy.projection(projection);
    return this;
  }

  @Override
  public SyncFindIterable<ResultT> sort(@Nullable final Bson sort) {
    proxy.sort(sort);
    return this;
  }
}
