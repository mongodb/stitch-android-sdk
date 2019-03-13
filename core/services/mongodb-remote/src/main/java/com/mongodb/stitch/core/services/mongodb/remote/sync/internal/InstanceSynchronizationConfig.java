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

import com.mongodb.Block;
import com.mongodb.MongoNamespace;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.DeleteManyModel;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonReader;
import org.bson.BsonValue;
import org.bson.BsonWriter;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;

class InstanceSynchronizationConfig
    implements Iterable<NamespaceSynchronizationConfig> {

  private final Map<MongoNamespace, NamespaceSynchronizationConfig> namespaces;
  private final MongoCollection<NamespaceSynchronizationConfig> namespacesColl;
  private final MongoCollection<CoreDocumentSynchronizationConfig> docsColl;
  private final ReadWriteLock instanceLock;

  InstanceSynchronizationConfig(final MongoDatabase configDb) {
    this.namespacesColl = configDb
        .getCollection("namespaces", NamespaceSynchronizationConfig.class);
    this.docsColl = configDb
        .getCollection("documents", CoreDocumentSynchronizationConfig.class);

    this.namespacesColl.createIndex(
        Indexes.ascending(
            NamespaceSynchronizationConfig.ConfigCodec.Fields.NAMESPACE_FIELD),
        new IndexOptions().unique(true));

    this.docsColl.createIndex(
        Indexes.ascending(
            CoreDocumentSynchronizationConfig.ConfigCodec.Fields.NAMESPACE_FIELD,
            CoreDocumentSynchronizationConfig.ConfigCodec.Fields.DOCUMENT_ID_FIELD),
        new IndexOptions().unique(true));

    // used to scan for stale documents when the namespace is marked as not stale,
    this.docsColl.createIndex(
        Indexes.ascending(
            CoreDocumentSynchronizationConfig.ConfigCodec.Fields.NAMESPACE_FIELD,
            CoreDocumentSynchronizationConfig.ConfigCodec.Fields.IS_STALE,
            CoreDocumentSynchronizationConfig.ConfigCodec.Fields.DOCUMENT_ID_FIELD));

    // used to scan for unpaused documents when the whole namespace is marked as stale
    this.docsColl.createIndex(
        Indexes.ascending(
            CoreDocumentSynchronizationConfig.ConfigCodec.Fields.NAMESPACE_FIELD,
            CoreDocumentSynchronizationConfig.ConfigCodec.Fields.IS_PAUSED,
            CoreDocumentSynchronizationConfig.ConfigCodec.Fields.DOCUMENT_ID_FIELD)
    );

    this.instanceLock = new ReentrantReadWriteLock();

    this.namespaces = new HashMap<>();
    // Fill from db
    namespacesColl.find().forEach(new Block<NamespaceSynchronizationConfig>() {
      @Override
      public void apply(
          @Nonnull final NamespaceSynchronizationConfig nsConfig
      ) {
        namespaces.put(nsConfig.getNamespace(), new NamespaceSynchronizationConfig(
            namespacesColl,
            docsColl,
            nsConfig));
      }
    });
  }

  InstanceSynchronizationConfig() {
    this.namespacesColl = null;
    this.docsColl = null;
    this.instanceLock = new ReentrantReadWriteLock();
    this.namespaces = new HashMap<>();
  }

  public NamespaceSynchronizationConfig getNamespaceConfig(
      final MongoNamespace namespace
  ) {
    instanceLock.readLock().lock();
    try {
      final NamespaceSynchronizationConfig config = namespaces.get(namespace);
      if (config != null) {
        return config;
      }
    } finally {
      instanceLock.readLock().unlock();
    }

    instanceLock.writeLock().lock();
    try {
      final NamespaceSynchronizationConfig config = namespaces.get(namespace);
      if (config != null) {
        return config;
      }
      final NamespaceSynchronizationConfig newConfig =
          new NamespaceSynchronizationConfig(namespacesColl, docsColl, namespace);
      namespacesColl.insertOne(newConfig);
      namespaces.put(namespace, newConfig);
      return newConfig;
    } finally {
      instanceLock.writeLock().unlock();
    }
  }

  public CoreDocumentSynchronizationConfig getSynchronizedDocument(
      final MongoNamespace namespace,
      final BsonValue documentId
  ) {
    return getNamespaceConfig(namespace).getSynchronizedDocument(documentId);
  }

  public boolean addSynchronizedDocuments(
      final MongoNamespace namespace,
      final BsonValue... documentIds
  ) {
    return getNamespaceConfig(namespace).addSynchronizedDocuments(documentIds);
  }

  public CoreDocumentSynchronizationConfig addAndGetSynchronizedDocument(
      final MongoNamespace namespace,
      final BsonValue documentId
  ) {
    final NamespaceSynchronizationConfig nsConfig = getNamespaceConfig(namespace);
    nsConfig.addSynchronizedDocument(documentId);
    return nsConfig.getSynchronizedDocument(documentId);
  }

  @Nullable
  DeleteManyModel<CoreDocumentSynchronizationConfig> removeSynchronizedDocuments(
      final MongoNamespace namespace,
      final BsonValue... documentIds
  ) {
    return getNamespaceConfig(namespace).removeSynchronizedDocuments(documentIds);
  }

  public boolean removeSynchronizedDocument(
      final MongoNamespace namespace,
      final BsonValue documentId
  ) {
    return getNamespaceConfig(namespace).removeSynchronizedDocument(documentId);
  }

  /**
   * Returns the set of synchronized namespaces.
   *
   * @return the set of synchronized namespaces.
   */
  public Set<MongoNamespace> getSynchronizedNamespaces() {
    instanceLock.readLock().lock();
    try {
      return new HashSet<>(namespaces.keySet());
    } finally {
      instanceLock.readLock().unlock();
    }
  }

  /**
   * Returns the set of synchronized documents in a namespace.
   *
   * @param namespace the namespace to get synchronized documents for.
   * @return the set of synchronized documents in a namespace.
   */
  public Set<CoreDocumentSynchronizationConfig> getSynchronizedDocuments(
      final MongoNamespace namespace
  ) {
    return getNamespaceConfig(namespace).getSynchronizedDocuments();
  }

  /**
   * Returns the set of synchronized documents _ids in a namespace.
   *
   * @param namespace the namespace to get synchronized documents _ids for.
   * @return the set of synchronized documents _ids in a namespace.
   */
  public Set<BsonValue> getSynchronizedDocumentIds(final MongoNamespace namespace) {
    try {
      return getNamespaceConfig(namespace).getSynchronizedDocumentIds();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new HashSet<>();
    }
  }

  @Override
  public Iterator<NamespaceSynchronizationConfig> iterator() {
    instanceLock.readLock().lock();
    try {
      return new ArrayList<>(namespaces.values()).iterator();
    } finally {
      instanceLock.readLock().unlock();
    }
  }

  BsonDocument toBsonDocument() {
    instanceLock.readLock().lock();
    try {
      final BsonDocument asDoc = new BsonDocument();
      asDoc.put(ConfigCodec.Fields.SCHEMA_VERSION_FIELD, new BsonInt32(1));
      return asDoc;
    } finally {
      instanceLock.readLock().unlock();
    }
  }

  static InstanceSynchronizationConfig fromBsonDocument(final BsonDocument document) {
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

    return new InstanceSynchronizationConfig();
  }

  static final ConfigCodec configCodec = new ConfigCodec();

  static final class ConfigCodec implements Codec<InstanceSynchronizationConfig> {

    @Override
    public InstanceSynchronizationConfig decode(
        final BsonReader reader,
        final DecoderContext decoderContext
    ) {
      final BsonDocument document = (new BsonDocumentCodec()).decode(reader, decoderContext);
      return fromBsonDocument(document);
    }

    @Override
    public void encode(
        final BsonWriter writer,
        final InstanceSynchronizationConfig value,
        final EncoderContext encoderContext
    ) {
      new BsonDocumentCodec().encode(writer, value.toBsonDocument(), encoderContext);
    }

    @Override
    public Class<InstanceSynchronizationConfig> getEncoderClass() {
      return InstanceSynchronizationConfig.class;
    }

    private static class Fields {
      static final String SCHEMA_VERSION_FIELD = "schema_version";
    }
  }
}
