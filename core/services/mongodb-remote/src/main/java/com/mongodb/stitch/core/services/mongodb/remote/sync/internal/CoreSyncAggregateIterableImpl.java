package com.mongodb.stitch.core.services.mongodb.remote.sync.internal;

import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteAggregateIterable;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoIterableImpl;
import com.mongodb.stitch.core.services.mongodb.remote.internal.Operation;
import com.mongodb.stitch.core.services.mongodb.remote.internal.Operations;
import com.mongodb.stitch.core.services.mongodb.remote.sync.CoreSyncAggregateIterable;

import org.bson.conversions.Bson;

import java.util.Collection;
import java.util.List;

class CoreSyncAggregateIterableImpl<DocumentT, ResultT>
    extends CoreSyncMongoIterableImpl<SyncOperations<DocumentT>, ResultT>
    implements CoreSyncAggregateIterable<ResultT> {

  private final List<? extends Bson> pipeline;

  CoreSyncAggregateIterableImpl(
      final List<? extends Bson> pipeline,
      final Class<ResultT> resultClass,
      final CoreStitchServiceClient service,
      final SyncOperations<DocumentT> operations
  ) {
    super(service, resultClass, operations);
    this.pipeline = pipeline;
  }

  @Override
  Operation<Collection<ResultT>> asOperation() {
    return getOperations().aggregate(pipeline, getResultClass());
  }
}
