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
import com.mongodb.client.model.WriteModel;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoCollection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;

class LocalSyncWriteModelContainer {
  private final MongoCollection<BsonDocument> localCollection;
  private final MongoCollection<BsonDocument> undoCollection;

  private final MongoCollectionWriteModelContainer<BsonDocument> localWrites;
  private final MongoCollectionWriteModelContainer<CoreDocumentSynchronizationConfig> configs;
  private final CoreRemoteMongoCollectionWriteModelContainer<BsonDocument> remoteWrites;

  private final Set<BsonValue> ids = new HashSet<>();

  private Runnable postCommit = null;

  LocalSyncWriteModelContainer(
      @Nonnull final MongoCollection<BsonDocument> localCollection,
      @Nonnull final CoreRemoteMongoCollection<BsonDocument> remoteCollection,
      @Nonnull final MongoCollection<BsonDocument> undoCollection,
      @Nonnull final MongoCollection<CoreDocumentSynchronizationConfig> docsCollection) {
    this.localCollection = localCollection;
    this.undoCollection = undoCollection;

    this.localWrites = new MongoCollectionWriteModelContainer<>(localCollection);
    this.configs = new MongoCollectionWriteModelContainer<>(docsCollection);
    this.remoteWrites = new CoreRemoteMongoCollectionWriteModelContainer<>(remoteCollection);
  }

  void addDocIDs(final BsonValue ...ids) {
    this.ids.addAll(Arrays.asList(ids));
  }

  void addLocalWrite(final WriteModel<BsonDocument> write) {
    localWrites.add(write);
  }

  void addRemoteWrite(final WriteModel<BsonDocument> write) {
    remoteWrites.add(write);
  }

  void addConfigWrite(final WriteModel<CoreDocumentSynchronizationConfig> config) {
    configs.add(config);
  }

  void merge(final LocalSyncWriteModelContainer localSyncWriteModelContainer) {
    if (localSyncWriteModelContainer == null) {
      return;
    }
    this.localWrites.merge(localSyncWriteModelContainer.localWrites);
    this.remoteWrites.merge(localSyncWriteModelContainer.remoteWrites);
    this.configs.merge(localSyncWriteModelContainer.configs);

    this.ids.addAll(localSyncWriteModelContainer.ids);
  }

  void wrapForRecovery(final Runnable callable) {
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

  void commitAndClear() {
    wrapForRecovery(() -> {
      localWrites.commitAndClear();
      configs.commitAndClear();
      remoteWrites.commitAndClear();
    });

    if (postCommit != null) {
      postCommit.run();
    }
  }

  LocalSyncWriteModelContainer withPostCommit(final Runnable runnable) {
    this.postCommit = runnable;
    return this;
  }
}
