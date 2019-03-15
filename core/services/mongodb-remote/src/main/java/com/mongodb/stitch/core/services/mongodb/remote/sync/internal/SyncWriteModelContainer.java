package com.mongodb.stitch.core.services.mongodb.remote.sync.internal;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.WriteModel;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

class SyncWriteModelContainer {
  final List<WriteModel<BsonDocument>> bulkWriteModels;
  final List<WriteModel<CoreDocumentSynchronizationConfig>> configs;
  final Set<BsonValue> ids;

  SyncWriteModelContainer(final WriteModel<CoreDocumentSynchronizationConfig> config,
                          final WriteModel<BsonDocument> writeModel,
                          final BsonValue... ids) {
    this();
    if (writeModel != null) {
      this.bulkWriteModels.add(writeModel);
    }
    if (config != null) {
      this.configs.add(config);
    }
    this.ids.addAll(Arrays.asList(ids));
  }

  SyncWriteModelContainer() {
    this.bulkWriteModels = new ArrayList<>();
    this.configs = new ArrayList<>();
    this.ids = new HashSet<>();
  }

  void merge(@Nullable final SyncWriteModelContainer syncWriteModelContainer) {
    if (syncWriteModelContainer == null) {
      return;
    }

    this.bulkWriteModels.addAll(syncWriteModelContainer.bulkWriteModels);
    this.configs.addAll(syncWriteModelContainer.configs);
    this.ids.addAll(syncWriteModelContainer.ids);
  }

  void wrapForRecovery(final MongoCollection<BsonDocument> localCollection,
                       final MongoCollection<BsonDocument> undoCollection,
                       final Runnable callable) {
    final List<BsonDocument> oldDocs = localCollection.find(
        new Document("_id", new Document("$in", ids))
    ).into(new ArrayList<>());

    if (oldDocs.size() > 0) {
      undoCollection.insertMany(oldDocs);
    }

    callable.run();

    if (oldDocs.size() > 0) {
      undoCollection.deleteMany(new Document("_id", new Document("$in", ids)));
    }
  }

  void commitAndClear(final MongoCollection<BsonDocument> localCollection,
                      final MongoCollection<BsonDocument> undoCollection,
                      final MongoCollection<CoreDocumentSynchronizationConfig> docsColl) {
    wrapForRecovery(localCollection, undoCollection, () -> {
      if (SyncWriteModelContainer.this.bulkWriteModels.size() > 0) {
        localCollection.bulkWrite(SyncWriteModelContainer.this.bulkWriteModels);
      }
      if (SyncWriteModelContainer.this.configs.size() > 0) {
        docsColl.bulkWrite(configs);
      }

      SyncWriteModelContainer.this.bulkWriteModels.clear();
      SyncWriteModelContainer.this.configs.clear();
    });
  }
}
