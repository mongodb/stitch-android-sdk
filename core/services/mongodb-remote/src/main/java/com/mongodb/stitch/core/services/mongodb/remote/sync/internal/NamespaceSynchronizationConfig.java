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

import static com.mongodb.stitch.core.internal.common.Assertions.keyPresent;
import static com.mongodb.stitch.core.services.mongodb.remote.sync.internal.CoreDocumentSynchronizationConfig.getDocFilter;

import com.mongodb.Block;
import com.mongodb.MongoNamespace;
import com.mongodb.client.DistinctIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.DeleteManyModel;
import com.mongodb.client.model.WriteModel;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonReader;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.BsonWriter;
import org.bson.Document;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;

class NamespaceSynchronizationConfig
    implements Iterable<CoreDocumentSynchronizationConfig> {

  private final MongoCollection<NamespaceSynchronizationConfig> namespacesColl;
  private final MongoCollection<CoreDocumentSynchronizationConfig> docsColl;
  private final MongoNamespace namespace;
  private final Map<BsonValue, CoreDocumentSynchronizationConfig> syncedDocuments;
  private final ReadWriteLock nsLock;

  private NamespaceListenerConfig namespaceListenerConfig;
  private ConflictHandler conflictHandler;
  private Codec documentCodec;

  NamespaceSynchronizationConfig(
      final MongoCollection<NamespaceSynchronizationConfig> namespacesColl,
      final MongoCollection<CoreDocumentSynchronizationConfig> docsColl,
      final MongoNamespace namespace
  ) {
    this.namespacesColl = namespacesColl;
    this.docsColl = docsColl;
    this.namespace = namespace;
    this.syncedDocuments = new HashMap<>();
    this.nsLock = new ReentrantReadWriteLock();

    // Fill from db
    final BsonDocument docsFilter = new BsonDocument();
    docsFilter.put(
        CoreDocumentSynchronizationConfig.ConfigCodec.Fields.NAMESPACE_FIELD,
        new BsonString(namespace.toString()));
    docsColl.find(docsFilter).forEach(new Block<CoreDocumentSynchronizationConfig>() {
      @Override
      public void apply(
          @Nonnull final CoreDocumentSynchronizationConfig docConfig
      ) {
        syncedDocuments.put(docConfig.getDocumentId(), new CoreDocumentSynchronizationConfig(
            docsColl,
            docConfig));
      }
    });
  }

  NamespaceSynchronizationConfig(
      final MongoCollection<NamespaceSynchronizationConfig> namespacesColl,
      final MongoCollection<CoreDocumentSynchronizationConfig> docsColl,
      final NamespaceSynchronizationConfig config
  ) {
    this.namespacesColl = namespacesColl;
    this.docsColl = docsColl;
    this.namespace = config.namespace;
    this.syncedDocuments = new HashMap<>();
    this.nsLock = config.nsLock;

    // Fill from db
    final BsonDocument docsFilter = new BsonDocument();
    docsFilter.put(
        CoreDocumentSynchronizationConfig.ConfigCodec.Fields.NAMESPACE_FIELD,
        new BsonString(namespace.toString()));
    docsColl.find(docsFilter).forEach(new Block<CoreDocumentSynchronizationConfig>() {
      @Override
      public void apply(
          @Nonnull final CoreDocumentSynchronizationConfig docConfig
      ) {
        syncedDocuments.put(docConfig.getDocumentId(), new CoreDocumentSynchronizationConfig(
            docsColl,
            docConfig));
      }
    });
  }

  private NamespaceSynchronizationConfig(
      final MongoNamespace namespace
  ) {
    this.namespace = namespace;
    this.namespacesColl = null;
    this.docsColl = null;
    this.syncedDocuments = null;
    this.nsLock = new ReentrantReadWriteLock();
  }

  <T> void configure(final ConflictHandler<T> conflictHandler,
                     final ChangeEventListener<T> changeEventListener,
                     final Codec<T> codec) {
    nsLock.writeLock().lock();
    try {
      this.conflictHandler = conflictHandler;
      this.namespaceListenerConfig = new NamespaceListenerConfig(changeEventListener, codec);
      this.documentCodec = codec;
    } finally {
      nsLock.writeLock().unlock();
    }
  }

  public MongoCollection<CoreDocumentSynchronizationConfig> getDocsColl() {
    return docsColl;
  }

  boolean isConfigured() {
    nsLock.readLock().lock();
    try {
      return this.conflictHandler != null;
    } finally {
      nsLock.readLock().unlock();
    }
  }

  static BsonDocument getNsFilter(
      final MongoNamespace namespace
  ) {
    final BsonDocument filter = new BsonDocument();
    filter.put(ConfigCodec.Fields.NAMESPACE_FIELD, new BsonString(namespace.toString()));
    return filter;
  }

  public MongoNamespace getNamespace() {
    return namespace;
  }

  NamespaceListenerConfig getNamespaceListenerConfig() {
    return namespaceListenerConfig;
  }

  public CoreDocumentSynchronizationConfig getSynchronizedDocument(final BsonValue documentId) {
    nsLock.readLock().lock();
    try {
      return syncedDocuments.get(documentId);
    } finally {
      nsLock.readLock().unlock();
    }
  }

  public Set<CoreDocumentSynchronizationConfig> getSynchronizedDocuments() {
    nsLock.readLock().lock();
    try {
      return new HashSet<>(syncedDocuments.values());
    } finally {
      nsLock.readLock().unlock();
    }
  }

  public Set<BsonValue> getSynchronizedDocumentIds() throws InterruptedException {
    nsLock.readLock().lockInterruptibly();
    try {
      return new HashSet<>(syncedDocuments.keySet());
    } finally {
      nsLock.readLock().unlock();
    }
  }

  Set<BsonValue> getStaleDocumentIds() {
    nsLock.readLock().lock();
    try {
      if (this.namespacesColl.count(
          getNsFilter(getNamespace())
              .append(ConfigCodec.Fields.IS_STALE, new BsonBoolean(true))
      ) != 0) {
        // If the entire namespace is stale, return all the document ids in the namespace that
        // are not paused.
        final DistinctIterable<BsonValue> unpausedStaleDocIds = this.docsColl.distinct(
            CoreDocumentSynchronizationConfig.ConfigCodec.Fields.DOCUMENT_ID_FIELD,
            getNsFilter(getNamespace()).append(
                CoreDocumentSynchronizationConfig.ConfigCodec.Fields.IS_PAUSED, BsonBoolean.FALSE),
            BsonValue.class);
        return unpausedStaleDocIds.into(new HashSet<>());
      } else {
        // Return just the stale documents that have been marked stale because they were
        // individually unpaused and marked as stale.
        final DistinctIterable<BsonValue> staleDocIds = this.docsColl.distinct(
            CoreDocumentSynchronizationConfig.ConfigCodec.Fields.DOCUMENT_ID_FIELD,
            getNsFilter(getNamespace()).append(
                CoreDocumentSynchronizationConfig.ConfigCodec.Fields.IS_STALE, BsonBoolean.TRUE),
            BsonValue.class);
        return staleDocIds.into(new HashSet<>());
      }
    } finally {
      nsLock.readLock().unlock();
    }
  }

  Codec getDocumentCodec() {
    return documentCodec;
  }

  boolean addSynchronizedDocuments(
      final BsonValue... documentIds
  ) {
    Map<BsonValue, CoreDocumentSynchronizationConfig> configs = new HashMap<>();
    for (final BsonValue documentId : documentIds) {
      if (getSynchronizedDocument(documentId) == null) {
        configs.put(
            documentId,
            new CoreDocumentSynchronizationConfig(docsColl, namespace, documentId));
      }
    }

    if (configs.size() > 0) {
      nsLock.writeLock().lock();
      try {
        docsColl.insertMany(new ArrayList<>(configs.values()));
        syncedDocuments.putAll(configs);
        return true;
      } finally {
        nsLock.writeLock().unlock();
      }
    }

    return false;
  }

  boolean addSynchronizedDocument(
      final BsonValue documentId
  ) {

    final CoreDocumentSynchronizationConfig newConfig;

    final CoreDocumentSynchronizationConfig existingConfig = getSynchronizedDocument(documentId);
    if (existingConfig != null) {
      return false;
    }

    newConfig = new CoreDocumentSynchronizationConfig(docsColl, namespace, documentId);

    nsLock.writeLock().lock();
    try {
      docsColl.insertOne(newConfig);
      syncedDocuments.put(documentId, newConfig);
      return true;
    } finally {
      nsLock.writeLock().unlock();
    }
  }

  @Nullable
  DeleteManyModel<CoreDocumentSynchronizationConfig> removeSynchronizedDocuments(
      final BsonValue... documentIds
  ) {
    nsLock.writeLock().lock();
    try {
      List<BsonValue> bsonValues = new ArrayList<>();
      for (final BsonValue documentId : documentIds) {
        final CoreDocumentSynchronizationConfig config = syncedDocuments.remove(documentId);
        if (config != null) {
          bsonValues.add(config.getDocumentId());
        }
      }

      if (bsonValues.size() > 0) {
        return new DeleteManyModel<CoreDocumentSynchronizationConfig>(
            CoreDocumentSynchronizationConfig.getDocsFilter(namespace, documentIds)
        );
      } else {
        return null;
      }
    } finally {
      nsLock.writeLock().unlock();
    }
  }

  public boolean removeSynchronizedDocument(final BsonValue documentId) {
    nsLock.writeLock().lock();
    try {
      if (syncedDocuments.containsKey(documentId)) {
        docsColl.deleteOne(getDocFilter(namespace, documentId));
        syncedDocuments.remove(documentId);
        return true;
      }
    } finally {
      nsLock.writeLock().unlock();
    }
    return false;
  }

  public ConflictHandler getConflictHandler() {
    nsLock.readLock().lock();
    try {
      return conflictHandler;
    } finally {
      nsLock.readLock().unlock();
    }
  }

  void setStale(final boolean stale) throws InterruptedException {
    nsLock.writeLock().lockInterruptibly();
    try {
      namespacesColl.updateOne(
          getNsFilter(getNamespace()),
          new BsonDocument("$set",
              new BsonDocument(
                  ConfigCodec.Fields.IS_STALE,
                  new BsonBoolean(stale)
              )));

      // if we're setting stale to be false, also mark any documents that were individually marked
      // as stale to not stale
      if (!stale) {
        docsColl.updateMany(
            getNsFilter(getNamespace())
                .append(
                    CoreDocumentSynchronizationConfig.ConfigCodec.Fields.IS_STALE,
                    BsonBoolean.TRUE),
            new BsonDocument("$set",
                new BsonDocument(
                    CoreDocumentSynchronizationConfig.ConfigCodec.Fields.IS_STALE,
                    BsonBoolean.FALSE))
        );
      }
    } catch (IllegalStateException e) {
      // eat this
    } finally {
      nsLock.writeLock().unlock();
    }
  }

  @Override
  @Nonnull
  public Iterator<CoreDocumentSynchronizationConfig> iterator() {
    nsLock.readLock().lock();
    try {
      return new ArrayList<>(syncedDocuments.values()).iterator();
    } finally {
      nsLock.readLock().unlock();
    }
  }

  BsonDocument toBsonDocument() {
    nsLock.readLock().lock();
    try {
      final BsonDocument asDoc = new BsonDocument();
      asDoc.put(ConfigCodec.Fields.NAMESPACE_FIELD, new BsonString(getNamespace().toString()));
      asDoc.put(ConfigCodec.Fields.SCHEMA_VERSION_FIELD, new BsonInt32(1));
      return asDoc;
    } finally {
      nsLock.readLock().unlock();
    }
  }

  ReadWriteLock getLock() {
    return nsLock;
  }

  static NamespaceSynchronizationConfig fromBsonDocument(final BsonDocument document) {
    keyPresent(ConfigCodec.Fields.NAMESPACE_FIELD, document);
    keyPresent(ConfigCodec.Fields.SCHEMA_VERSION_FIELD, document);

    final int schemaVersion =
        document.getNumber(ConfigCodec.Fields.SCHEMA_VERSION_FIELD).intValue();
    if (schemaVersion != 1) {
      throw new IllegalStateException(
          String.format(
              "unexpected schema version '%d' for %s",
              schemaVersion,
              CoreDocumentSynchronizationConfig.class.getSimpleName()));
    }

    return new NamespaceSynchronizationConfig(
        new MongoNamespace(document.getString(ConfigCodec.Fields.NAMESPACE_FIELD).getValue()));
  }

  static final ConfigCodec configCodec = new ConfigCodec();

  static final class ConfigCodec implements Codec<NamespaceSynchronizationConfig> {

    @Override
    public NamespaceSynchronizationConfig decode(
        final BsonReader reader,
        final DecoderContext decoderContext
    ) {
      final BsonDocument document = (new BsonDocumentCodec()).decode(reader, decoderContext);
      return fromBsonDocument(document);
    }

    @Override
    public void encode(
        final BsonWriter writer,
        final NamespaceSynchronizationConfig value,
        final EncoderContext encoderContext
    ) {
      new BsonDocumentCodec().encode(writer, value.toBsonDocument(), encoderContext);
    }

    @Override
    public Class<NamespaceSynchronizationConfig> getEncoderClass() {
      return NamespaceSynchronizationConfig.class;
    }

    static class Fields {
      static final String NAMESPACE_FIELD = "namespace";
      static final String SCHEMA_VERSION_FIELD = "schema_version";
      static final String IS_STALE = "is_stale";
    }
  }
}
