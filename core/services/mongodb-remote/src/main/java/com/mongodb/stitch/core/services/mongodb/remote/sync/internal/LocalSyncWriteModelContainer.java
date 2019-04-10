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
import com.mongodb.stitch.core.services.mongodb.remote.ChangeEvent;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoCollection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.annotation.Nonnull;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;

class LocalSyncWriteModelContainer {
  private final NamespaceSynchronizationConfig nsConfig;
  private final MongoCollection<BsonDocument> localCollection;
  private final MongoCollection<BsonDocument> undoCollection;

  private final MongoCollectionWriteModelContainer<BsonDocument> localWrites;
  private final MongoCollectionWriteModelContainer<CoreDocumentSynchronizationConfig> configWrites;
  private final CoreRemoteMongoCollectionWriteModelContainer<BsonDocument> remoteWrites;
  private final List<ChangeEvent<BsonDocument>> localChangeEvents;
  private final EventDispatcher eventDispatcher;

  private final Set<BsonValue> ids = new HashSet<>();

  private Runnable postCommit = null;

  LocalSyncWriteModelContainer(
      @Nonnull final NamespaceSynchronizationConfig nsConfig,
      @Nonnull final MongoCollection<BsonDocument> localCollection,
      @Nonnull final CoreRemoteMongoCollection<BsonDocument> remoteCollection,
      @Nonnull final MongoCollection<BsonDocument> undoCollection,
      @Nonnull final EventDispatcher eventDispatcher) {
    this.nsConfig = nsConfig;
    this.localCollection = localCollection;
    this.undoCollection = undoCollection;
    this.eventDispatcher = eventDispatcher;

    MongoCollection<CoreDocumentSynchronizationConfig> docsCollection = nsConfig.getDocsColl();
    this.localWrites = new MongoCollectionWriteModelContainer<>(localCollection);
    this.configWrites = new MongoCollectionWriteModelContainer<>(docsCollection);
    this.remoteWrites = new CoreRemoteMongoCollectionWriteModelContainer<>(remoteCollection);
    this.localChangeEvents = new ArrayList<>();
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
    configWrites.add(config);
  }

  void addLocalChangeEvent(final ChangeEvent<BsonDocument> localChangeEvent) {
    localChangeEvents.add(localChangeEvent);
  }

  void merge(final LocalSyncWriteModelContainer localSyncWriteModelContainer) {
    if (localSyncWriteModelContainer == null) {
      return;
    }
    this.localWrites.merge(localSyncWriteModelContainer.localWrites);
    this.remoteWrites.merge(localSyncWriteModelContainer.remoteWrites);
    this.configWrites.merge(localSyncWriteModelContainer.configWrites);

    this.ids.addAll(localSyncWriteModelContainer.ids);
    this.localChangeEvents.addAll(localSyncWriteModelContainer.localChangeEvents);
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
      configWrites.commitAndClear();
      remoteWrites.commitAndClear();
    });

    if (true) { // temporary: change this to a success indicator
      int numEvents = localChangeEvents.size();
      for (int i = 0; i < numEvents; i++) {
        ChangeEvent<BsonDocument> event = localChangeEvents.get(i);
        eventDispatcher.emitEvent(nsConfig, event);
      }
      localChangeEvents.clear();
    }

    if (postCommit != null) {
      postCommit.run();
    }
  }

  LocalSyncWriteModelContainer withPostCommit(final Runnable runnable) {
    this.postCommit = runnable;
    return this;
  }
}
