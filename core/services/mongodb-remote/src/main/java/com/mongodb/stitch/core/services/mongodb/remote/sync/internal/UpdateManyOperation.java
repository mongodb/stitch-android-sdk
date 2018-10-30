package com.mongodb.stitch.core.services.mongodb.remote.sync.internal;

import com.mongodb.MongoNamespace;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.internal.Operation;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncUpdateOptions;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncUpdateResult;

import org.bson.BsonDocument;
import org.bson.conversions.Bson;

import javax.annotation.Nullable;

class UpdateManyOperation implements Operation<SyncUpdateResult> {

  private final MongoNamespace namespace;
  private final Bson filter;
  private final BsonDocument update;
  private final DataSynchronizer dataSynchronizer;
  private final SyncUpdateOptions syncUpdateOptions;

  UpdateManyOperation(
      final MongoNamespace namespace,
      final Bson filter,
      final BsonDocument update,
      final DataSynchronizer dataSynchronizer,
      final SyncUpdateOptions syncUpdateOptions
  ) {
    this.namespace = namespace;
    this.filter = filter;
    this.update = update;
    this.dataSynchronizer = dataSynchronizer;
    this.syncUpdateOptions = syncUpdateOptions;
  }

  public SyncUpdateResult execute(@Nullable final CoreStitchServiceClient service) {
    final UpdateResult localResult = this.dataSynchronizer.updateMany(
        namespace,
        filter,
        update,
        new UpdateOptions().upsert(this.syncUpdateOptions.isUpsert()));

    return new SyncUpdateResult(
        localResult.getMatchedCount(),
        localResult.getModifiedCount(),
        localResult.getUpsertedId()
    );
  }
}
