package com.mongodb.stitch.core.services.mongodb.remote.sync.internal;

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.internal.common.BsonUtils;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.internal.Operation;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncInsertManyResult;

import org.bson.BsonDocument;
import org.bson.BsonValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

class InsertManyAndSyncOperation implements Operation<SyncInsertManyResult> {

  private final MongoNamespace namespace;
  private final List<BsonDocument> documents;
  private final DataSynchronizer dataSynchronizer;

  InsertManyAndSyncOperation(
      final MongoNamespace namespace,
      final List<BsonDocument> documents,
      final DataSynchronizer dataSynchronizer
  ) {
    this.namespace = namespace;
    this.documents = documents;
    this.dataSynchronizer = dataSynchronizer;
  }

  public SyncInsertManyResult execute(@Nullable final CoreStitchServiceClient service) {
    this.dataSynchronizer.insertManyAndSync(namespace, documents);
    final Map<Long, BsonValue> indexToId = new HashMap<>();
    for (int i = 0; i < this.documents.size(); i++) {
      indexToId.put((long)i, BsonUtils.getDocumentId(this.documents.get(i)));
    }
    return new SyncInsertManyResult(indexToId);
  }
}
