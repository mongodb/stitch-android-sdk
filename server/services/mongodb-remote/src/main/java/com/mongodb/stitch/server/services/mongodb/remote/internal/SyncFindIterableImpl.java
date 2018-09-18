package com.mongodb.stitch.server.services.mongodb.remote.internal;

import com.mongodb.stitch.server.services.mongodb.remote.SyncFindIterable;
import com.mongodb.stitch.core.services.mongodb.remote.sync.CoreSyncFindIterable;

import org.bson.conversions.Bson;

public class SyncFindIterableImpl<ResultT>
  extends RemoteMongoIterableImpl<ResultT>
  implements SyncFindIterable<ResultT> {

  private final CoreSyncFindIterable<ResultT> proxy;

  SyncFindIterableImpl(
    final CoreSyncFindIterable<ResultT> iterable
  ) {
    super(iterable);
    this.proxy = iterable;
  }

  @Override
  public SyncFindIterable<ResultT> filter(final Bson filter) {
    proxy.filter(filter);
    return this;
  }

  @Override
  public SyncFindIterable<ResultT> limit(final int limit) {
    proxy.limit(limit);
    return this;
  }

  @Override
  public SyncFindIterable<ResultT> projection(final Bson projection) {
    proxy.projection(projection);
    return this;
  }

  @Override
  public SyncFindIterable<ResultT> sort(final Bson sort) {
    proxy.sort(sort);
    return this;
  }
}
