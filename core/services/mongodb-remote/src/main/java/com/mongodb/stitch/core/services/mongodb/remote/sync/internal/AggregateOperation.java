package com.mongodb.stitch.core.services.mongodb.remote.sync.internal;

import com.mongodb.MongoNamespace;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.internal.Operation;
import com.mongodb.stitch.core.services.mongodb.remote.sync.CoreSyncAggregateIterable;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncDeleteResult;

import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import javax.annotation.Nullable;

class AggregateOperation<T> implements Operation<Collection<T>> {
  private final MongoNamespace namespace;
  private final DataSynchronizer dataSynchronizer;
  private final List<? extends Bson> pipeline;
  private final Class<T> resultClass;

  AggregateOperation(
      final MongoNamespace namespace,
      final DataSynchronizer dataSynchronizer,
      final List<? extends Bson> pipeline,
      final Class<T> resultClass
  ) {
    this.namespace = namespace;
    this.dataSynchronizer = dataSynchronizer;
    this.pipeline = pipeline;
    this.resultClass = resultClass;
  }

  public Collection<T> execute(@Nullable final CoreStitchServiceClient service) {
    return this.dataSynchronizer.aggregate(namespace, pipeline, resultClass).into(new ArrayList<>());
  }
}

