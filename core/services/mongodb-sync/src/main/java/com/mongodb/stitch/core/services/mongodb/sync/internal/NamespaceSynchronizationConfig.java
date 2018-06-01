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

package com.mongodb.stitch.core.services.mongodb.sync.internal;

import static com.mongodb.stitch.core.internal.common.Assertions.keyPresent;
import static com.mongodb.stitch.core.services.mongodb.sync.internal.CoreDocumentSynchronizationConfig.getDocFilter;

import com.mongodb.Block;
import com.mongodb.MongoNamespace;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.stitch.core.services.mongodb.sync.SyncConflictResolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.Nonnull;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonReader;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.BsonWriter;
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
  private BsonValue lastRemoteResumeToken;

  NamespaceSynchronizationConfig(
      final MongoCollection<NamespaceSynchronizationConfig> namespacesColl,
      final MongoCollection<CoreDocumentSynchronizationConfig> docsColl,
      final MongoNamespace namespace
  ) {
    this.namespacesColl = namespacesColl;
    this.docsColl = docsColl;
    this.namespace = namespace;
    this.lastRemoteResumeToken = null;
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
            docConfig,
            null,
            null));
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
    this.lastRemoteResumeToken = config.lastRemoteResumeToken;
    this.syncedDocuments = new HashMap<>();
    this.nsLock = config.nsLock;

    // Fill from db
    docsColl.find().forEach(new Block<CoreDocumentSynchronizationConfig>() {
      @Override
      public void apply(
          @Nonnull final CoreDocumentSynchronizationConfig docConfig
      ) {
        syncedDocuments.put(docConfig.getDocumentId(), new CoreDocumentSynchronizationConfig(
            docsColl,
            docConfig,
            null,
            null));
      }
    });
  }

  private NamespaceSynchronizationConfig(
      final MongoNamespace namespace,
      final BsonValue lastRemoteResumeToken
  ) {
    this.namespace = namespace;
    this.lastRemoteResumeToken = lastRemoteResumeToken;
    this.namespacesColl = null;
    this.docsColl = null;
    this.syncedDocuments = null;
    this.nsLock = new ReentrantReadWriteLock();
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

  public boolean isDocumentSynchronized(final BsonValue documentId) {
    nsLock.readLock().lock();
    try {
      return syncedDocuments.containsKey(documentId);
    } finally {
      nsLock.readLock().unlock();
    }
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

  public Set<BsonValue> getSynchronizedDocumentIds() {
    nsLock.readLock().lock();
    try {
      return new HashSet<>(syncedDocuments.keySet());
    } finally {
      nsLock.readLock().unlock();
    }
  }

  public <T> CoreDocumentSynchronizationConfig addSynchronizedDocument(
      final MongoNamespace namespace,
      final BsonValue documentId,
      final SyncConflictResolver<T> conflictResolver,
      final Codec<T> documentCodec
  ) {

    final CoreDocumentSynchronizationConfig newConfig;

    final CoreDocumentSynchronizationConfig existingConfig = getSynchronizedDocument(documentId);
    if (existingConfig == null) {
      newConfig = new CoreDocumentSynchronizationConfig(
          docsColl,
          namespace,
          documentId,
          conflictResolver,
          documentCodec);
    } else {
      newConfig = new CoreDocumentSynchronizationConfig(
          docsColl,
          existingConfig,
          conflictResolver,
          documentCodec);
    }

    nsLock.writeLock().lock();
    try {
      docsColl.replaceOne(
          getDocFilter(newConfig.getNamespace(), newConfig.getDocumentId()),
          newConfig,
          new ReplaceOptions().upsert(true));
      syncedDocuments.put(documentId, newConfig);
      return newConfig;
    } finally {
      nsLock.writeLock().unlock();
    }
  }

  public void removeSynchronizedDocument(final BsonValue documentId) {
    nsLock.writeLock().lock();
    try {
      docsColl.deleteOne(getDocFilter(namespace, documentId));
      syncedDocuments.remove(documentId);
    } finally {
      nsLock.writeLock().unlock();
    }
  }

  public BsonValue getLastRemoteResumeToken() {
    nsLock.readLock().lock();
    try {
      return lastRemoteResumeToken;
    } finally {
      nsLock.readLock().unlock();
    }
  }

  public void setLastRemoteResumeToken(final BsonValue lastRemoteResumeToken) {
    nsLock.writeLock().lock();
    try {
      this.lastRemoteResumeToken = lastRemoteResumeToken;
      namespacesColl.replaceOne(
          getNsFilter(getNamespace()), this);
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
      if (getLastRemoteResumeToken() != null) {
        asDoc.put(ConfigCodec.Fields.LAST_REMOTE_RESUME_TOKEN_FIELD, lastRemoteResumeToken);
      }
      return asDoc;
    } finally {
      nsLock.readLock().unlock();
    }
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

    final BsonValue lastRemoteResumeToken;
    if (document.containsKey(ConfigCodec.Fields.LAST_REMOTE_RESUME_TOKEN_FIELD)) {
      lastRemoteResumeToken = document.get(ConfigCodec.Fields.LAST_REMOTE_RESUME_TOKEN_FIELD);
    } else {
      lastRemoteResumeToken = null;
    }

    return new NamespaceSynchronizationConfig(
        new MongoNamespace(document.getString(ConfigCodec.Fields.NAMESPACE_FIELD).getValue()),
        lastRemoteResumeToken);
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
      static final String LAST_REMOTE_RESUME_TOKEN_FIELD = "last_remote_resume_token";
    }
  }
}
