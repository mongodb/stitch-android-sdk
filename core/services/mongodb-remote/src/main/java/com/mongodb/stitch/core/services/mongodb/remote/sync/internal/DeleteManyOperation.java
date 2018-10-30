package com.mongodb.stitch.core.services.mongodb.remote.sync.internal;

import com.mongodb.MongoNamespace;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.internal.Operation;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncDeleteResult;

import org.bson.conversions.Bson;

import javax.annotation.Nullable;

class DeleteManyOperation implements Operation<SyncDeleteResult> {
  private final MongoNamespace namespace;
  private final Bson filter;
  private final DataSynchronizer dataSynchronizer;

  DeleteManyOperation(
      final MongoNamespace namespace,
      final Bson filter,
      final DataSynchronizer dataSynchronizer
  ) {
    this.namespace = namespace;
    this.filter = filter;
    this.dataSynchronizer = dataSynchronizer;
  }

  public SyncDeleteResult execute(@Nullable final CoreStitchServiceClient service) {
    final DeleteResult localResult = this.dataSynchronizer.deleteMany(namespace, filter);
    return new SyncDeleteResult(localResult.getDeletedCount());
  }
}
