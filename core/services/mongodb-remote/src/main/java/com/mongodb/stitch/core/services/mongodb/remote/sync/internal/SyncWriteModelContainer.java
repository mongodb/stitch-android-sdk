/*
 * Copyright 2018-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.stitch.core.services.mongodb.remote.sync.internal;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.DeleteManyModel;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.RawBsonDocument;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

class SyncWriteModelContainer {
  final List<WriteModel<BsonDocument>> bulkWriteModels;
  final List<WriteModel<CoreDocumentSynchronizationConfig>> configs;
  final Set<BsonValue> ids;
  private final CodecRegistry codecRegistry;
  private int size;

  SyncWriteModelContainer(final CodecRegistry codecRegistry,
                          final WriteModel<CoreDocumentSynchronizationConfig> config,
                          final WriteModel<BsonDocument> writeModel,
                          final BsonValue... ids) {
    this(codecRegistry);
    if (writeModel != null) {
      this.bulkWriteModels.add(writeModel);
      size += getModelSize(writeModel);
    }
    if (config != null) {
      this.configs.add(config);
      size += getModelSize(config);
    }
    this.ids.addAll(Arrays.asList(ids));
  }

  SyncWriteModelContainer(final CodecRegistry codecRegistry) {
    this.bulkWriteModels = new ArrayList<>();
    this.configs = new ArrayList<>();
    this.ids = new HashSet<>();
    this.codecRegistry = codecRegistry;
  }

  int getSize() {
    return size;
  }

  void merge(@Nullable final SyncWriteModelContainer syncWriteModelContainer) {
    if (syncWriteModelContainer == null) {
      return;
    }

    this.bulkWriteModels.addAll(syncWriteModelContainer.bulkWriteModels);
    this.configs.addAll(syncWriteModelContainer.configs);
    this.ids.addAll(syncWriteModelContainer.ids);

    for (final WriteModel<BsonDocument> model : syncWriteModelContainer.bulkWriteModels) {
      size += getModelSize(model);
    }
    for (final WriteModel<CoreDocumentSynchronizationConfig> model
        : syncWriteModelContainer.configs) {
      size += getModelSize(model);
    }
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
      if (bulkWriteModels.size() > 0) {
        localCollection.bulkWrite(bulkWriteModels);
      }
      if (configs.size() > 0) {
        docsColl.bulkWrite(configs);
      }
    });
    bulkWriteModels.clear();
    configs.clear();
    size = 0;
    ids.clear();
  }

  private int getSizeOf(final Bson bson) {
    final RawBsonDocument rawDoc;
    if (bson instanceof RawBsonDocument) {
      rawDoc = (RawBsonDocument) bson;
    } else {
      rawDoc = new RawBsonDocument(
          bson.toBsonDocument(null, codecRegistry), new BsonDocumentCodec());
    }
    return rawDoc.getByteBuffer().remaining();
  }

  @SuppressWarnings("unchecked")
  private int getModelSize(final WriteModel<? extends Bson> model) {
    int size = 0;
    if (model instanceof DeleteManyModel) {
      final DeleteManyModel<? extends Bson> deleteModel = (DeleteManyModel<? extends Bson>) model;
      size += getSizeOf(deleteModel.getFilter());
    } else if (model instanceof DeleteOneModel) {
      final DeleteOneModel<? extends Bson> deleteModel = (DeleteOneModel<? extends Bson>)(model);
      size += getSizeOf(deleteModel.getFilter());
    } else if (model instanceof InsertOneModel) {
      final InsertOneModel<? extends Bson> insertModel = (InsertOneModel<? extends Bson>)(model);
      size += getSizeOf(insertModel.getDocument());
    } else if (model instanceof ReplaceOneModel) {
      final ReplaceOneModel<? extends Bson> replaceModel = (ReplaceOneModel<? extends Bson>)(model);
      size += getSizeOf(replaceModel.getFilter());
      size += getSizeOf(replaceModel.getReplacement());
    } else if (model instanceof UpdateManyModel) {
      final UpdateManyModel<? extends Bson> updateModel = (UpdateManyModel<? extends Bson>)(model);
      size += getSizeOf(updateModel.getFilter());
      size += getSizeOf(updateModel.getUpdate());
    } else if (model instanceof UpdateOneModel) {
      final UpdateOneModel<? extends Bson> updateModel = (UpdateOneModel<? extends Bson>)(model);
      size += getSizeOf(updateModel.getFilter());
      size += getSizeOf(updateModel.getUpdate());
    } else {
      throw new IllegalArgumentException("unknown model");
    }
    return size;
  }
}
