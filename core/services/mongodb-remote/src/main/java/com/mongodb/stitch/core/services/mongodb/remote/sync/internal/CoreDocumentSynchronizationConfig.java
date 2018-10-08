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

import com.mongodb.MongoNamespace;
import com.mongodb.client.MongoCollection;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.annotation.Nonnull;

import org.bson.BsonArray;
import org.bson.BsonBinary;
import org.bson.BsonBinaryReader;
import org.bson.BsonBinaryWriter;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonReader;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.BsonWriter;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;
import org.bson.io.OutputBuffer;


class CoreDocumentSynchronizationConfig {
  private final MongoCollection<CoreDocumentSynchronizationConfig> docsColl;
  private final MongoNamespace namespace;
  private final BsonValue documentId;
  private final ReadWriteLock docLock;
  private ChangeEvent<BsonDocument> lastUncommittedChangeEvent;
  private long lastResolution;
  private BsonValue lastKnownRemoteVersion;
  private boolean isStale;
  private boolean isFrozen;

  // TODO: How can this be trimmed? The same version could appear after we see it once. That
  // may be a non-issue.
  // TODO: To get rid of this, an ordering on versions would be needed and would have to be
  // abided by other clients sync and non-sync alike.
  private Set<BsonValue> committedVersions;

  CoreDocumentSynchronizationConfig(
      final MongoCollection<CoreDocumentSynchronizationConfig> docsColl,
      final MongoNamespace namespace,
      final BsonValue documentId
  ) {
    this.docsColl = docsColl;
    this.namespace = namespace;
    this.documentId = documentId;
    this.docLock = new ReentrantReadWriteLock();
    this.lastResolution = -1;
    this.lastKnownRemoteVersion = null;
    this.committedVersions = new HashSet<>();
    this.lastUncommittedChangeEvent = null;
    this.isStale = false;
  }

  CoreDocumentSynchronizationConfig(
      final MongoCollection<CoreDocumentSynchronizationConfig> docsColl,
      final CoreDocumentSynchronizationConfig config
  ) {
    this.docsColl = docsColl;
    this.namespace = config.namespace;
    this.documentId = config.documentId;
    this.docLock = config.docLock;
    this.lastResolution = config.lastResolution;
    this.lastKnownRemoteVersion = config.lastKnownRemoteVersion;
    this.committedVersions = config.committedVersions;
    this.lastUncommittedChangeEvent = config.lastUncommittedChangeEvent;
    this.isStale = config.isStale;
  }

  private CoreDocumentSynchronizationConfig(
      final MongoNamespace namespace,
      final BsonValue documentId,
      final ChangeEvent<BsonDocument> lastUncommittedChangeEvent,
      final long lastResolution,
      final BsonValue lastVersion,
      final Set<BsonValue> committedVersions,
      final boolean isStale,
      final boolean isFrozen
  ) {
    this.namespace = namespace;
    this.documentId = documentId;
    this.lastResolution = lastResolution;
    this.lastKnownRemoteVersion = lastVersion;
    this.committedVersions = committedVersions;
    this.lastUncommittedChangeEvent = lastUncommittedChangeEvent;
    this.docLock = new ReentrantReadWriteLock();
    this.docsColl = null;
    this.isStale = isStale;
    this.isFrozen = isFrozen;
  }

  static BsonDocument getDocFilter(
      @Nonnull final MongoNamespace namespace,
      @Nonnull final BsonValue documentId
  ) {
    final BsonDocument filter = new BsonDocument();
    filter.put(ConfigCodec.Fields.NAMESPACE_FIELD, new BsonString(namespace.toString()));
    filter.put(ConfigCodec.Fields.DOCUMENT_ID_FIELD, documentId);
    return filter;
  }

  public boolean isStale() {
    docLock.readLock().lock();
    try {
      final BsonDocument filter = getDocFilter(namespace, documentId);
      filter.append(
          CoreDocumentSynchronizationConfig.ConfigCodec.Fields.IS_STALE, BsonBoolean.TRUE);
      return docsColl.countDocuments(filter) == 1;
    } finally {
      docLock.readLock().unlock();
    }
  }

  public void setStale(final boolean stale) {
    docLock.writeLock().lock();
    try {
      docsColl.updateOne(
          getDocFilter(namespace, documentId),
          new BsonDocument("$set",
              new BsonDocument(
                  CoreDocumentSynchronizationConfig.ConfigCodec.Fields.IS_STALE,
                  new BsonBoolean(stale))));
      isStale = stale;
    } catch (IllegalStateException e) {
      // eat this
    } finally {
      docLock.writeLock().unlock();
    }
  }

  /**
   * In the event that an irrecoverable error has occurred,
   * updates should no longer be processed for a document.
   * The config should reflect that it is in a frozen state.
   *
   * When a new write operation happens to this document locally
   * we should unfreeze the document and resume trying to sync it.
   *
   * @param isFrozen whether or not this config is frozen
   */
  void setFrozen(boolean isFrozen) {
    this.isFrozen = isFrozen;
  }

  boolean isFrozen() {
    System.out.println(String.format("FROZEN %b", isFrozen));
    return isFrozen;
  }

  /**
   * Sets that there are some pending writes that occurred at a time for an associated
   * locally emitted change event. This variant maintains the last version set.
   *
   * @param atTime      the time at which the write occurred.
   * @param changeEvent the description of the write/change.
   */
  void setSomePendingWrites(
      final long atTime,
      final ChangeEvent<BsonDocument> changeEvent
  ) {
    docLock.writeLock().lock();
    try {
      this.lastUncommittedChangeEvent =
          coalesceChangeEvents(this.lastUncommittedChangeEvent, changeEvent);
      this.lastResolution = atTime;
      docsColl.replaceOne(
          getDocFilter(namespace, documentId),
          this);
    } finally {
      docLock.writeLock().unlock();
    }
  }

  /**
   * Sets that there are some pending writes that occurred at a time for an associated
   * locally emitted change event. This variant updates the last version set.
   *
   * @param atTime      the time at which the write occurred.
   * @param atVersion   the version for which the write occurred.
   * @param changeEvent the description of the write/change.
   */
  void setSomePendingWrites(
      final long atTime,
      final BsonValue atVersion,
      final ChangeEvent<BsonDocument> changeEvent
  ) {
    docLock.writeLock().lock();
    try {
      this.lastUncommittedChangeEvent = changeEvent;
      this.lastResolution = atTime;
      this.lastKnownRemoteVersion = atVersion;

      if (atVersion != null) {
        this.committedVersions.add(atVersion);
      }
      docsColl.replaceOne(
          getDocFilter(namespace, documentId),
          this);
    } finally {
      docLock.writeLock().unlock();
    }
  }

  void setPendingWritesComplete(final BsonValue atVersion) {
    // Pending writes are only completed during sync passes.
    // If this document is frozen, we should not process this update.
//    if (isFrozen) {
//      return;
//    }

    docLock.writeLock().lock();
    try {
      this.lastUncommittedChangeEvent = null;
      this.lastKnownRemoteVersion = atVersion;
      if (atVersion != null) {
        this.committedVersions.add(atVersion);
      }
      docsColl.replaceOne(
          getDocFilter(namespace, documentId),
          this);
    } finally {
      docLock.writeLock().unlock();
    }
  }

  // Equality on documentId
  @Override
  public boolean equals(final Object object) {
    docLock.readLock().lock();
    try {
      if (this == object) {
        return true;
      }
      if (!(object instanceof CoreDocumentSynchronizationConfig)) {
        return false;
      }
      final CoreDocumentSynchronizationConfig other = (CoreDocumentSynchronizationConfig) object;
      return getDocumentId().equals(other.getDocumentId());
    } finally {
      docLock.readLock().unlock();
    }
  }

  // Hash on documentId
  @Override
  public int hashCode() {
    docLock.readLock().lock();
    try {
      return super.hashCode()
          + getDocumentId().hashCode();
    } finally {
      docLock.readLock().unlock();
    }
  }

  public BsonValue getDocumentId() {
    docLock.readLock().lock();
    try {
      return documentId;
    } finally {
      docLock.readLock().unlock();
    }
  }

  public MongoNamespace getNamespace() {
    docLock.readLock().lock();
    try {
      return namespace;
    } finally {
      docLock.readLock().unlock();
    }
  }

  public boolean hasUncommittedWrites() {
    docLock.readLock().lock();
    try {
      return lastUncommittedChangeEvent != null;
    } finally {
      docLock.readLock().unlock();
    }
  }

  public ChangeEvent<BsonDocument> getLastUncommittedChangeEvent() {
    docLock.readLock().lock();
    try {
      return lastUncommittedChangeEvent;
    } finally {
      docLock.readLock().unlock();
    }
  }

  public long getLastResolution() {
    docLock.readLock().lock();
    try {
      return lastResolution;
    } finally {
      docLock.readLock().unlock();
    }
  }

  public BsonValue getLastKnownRemoteVersion() {
    docLock.readLock().lock();
    try {
      return lastKnownRemoteVersion;
    } finally {
      docLock.readLock().unlock();
    }
  }

  public boolean hasCommittedVersion(final BsonValue version) {
    docLock.readLock().lock();
    try {
      return committedVersions.contains(version);
    } finally {
      docLock.readLock().unlock();
    }
  }

  /**
   * Possibly coalesces the newest change event to match the user's original intent. For example,
   * an unsynchronized insert and update is still an insert.
   *
   * @param lastUncommittedChangeEvent the last change event known about for a document.
   * @param newestChangeEvent          the newest change event known about for a document.
   * @return the possibly coalesced change event.
   */
  private static ChangeEvent<BsonDocument> coalesceChangeEvents(
      final ChangeEvent<BsonDocument> lastUncommittedChangeEvent,
      final ChangeEvent<BsonDocument> newestChangeEvent
  ) {
    if (lastUncommittedChangeEvent == null) {
      return newestChangeEvent;
    }
    switch (lastUncommittedChangeEvent.getOperationType()) {
      case INSERT:
        switch (newestChangeEvent.getOperationType()) {
          // Coalesce replaces/updates to inserts since we believe at some point a document did not
          // exist remotely and that this replace or update should really be an insert if we are
          // still in an uncommitted state.
          case REPLACE:
          case UPDATE:
            return new ChangeEvent<>(
                newestChangeEvent.getId(),
                ChangeEvent.OperationType.INSERT,
                newestChangeEvent.getFullDocument(),
                newestChangeEvent.getNamespace(),
                newestChangeEvent.getDocumentKey(),
                null,
                newestChangeEvent.hasUncommittedWrites()
            );
          default:
            break;
        }
        break;
      case DELETE:
        switch (newestChangeEvent.getOperationType()) {
          // Coalesce inserts to updates since we believe at some point a document existed remotely
          // and that this insert should really be an update if we are still in an uncommitted
          // state.
          case INSERT:
            return new ChangeEvent<>(
                newestChangeEvent.getId(),
                ChangeEvent.OperationType.UPDATE,
                newestChangeEvent.getFullDocument(),
                newestChangeEvent.getNamespace(),
                newestChangeEvent.getDocumentKey(),
                null,
                newestChangeEvent.hasUncommittedWrites()
            );
          default:
            break;
        }
        break;
      default:
        break;
    }
    return newestChangeEvent;
  }

  BsonDocument toBsonDocument() {
    docLock.readLock().lock();
    try {
      final BsonDocument asDoc = new BsonDocument();
      asDoc.put(ConfigCodec.Fields.DOCUMENT_ID_FIELD, getDocumentId());
      asDoc.put(ConfigCodec.Fields.SCHEMA_VERSION_FIELD, new BsonInt32(1));
      asDoc.put(ConfigCodec.Fields.NAMESPACE_FIELD, new BsonString(getNamespace().toString()));
      asDoc.put(ConfigCodec.Fields.LAST_RESOLUTION_FIELD, new BsonInt64(getLastResolution()));
      if (getLastKnownRemoteVersion() != null) {
        asDoc.put(ConfigCodec.Fields.LAST_KNOWN_REMOTE_VERSION_FIELD, getLastKnownRemoteVersion());
      }
      if (getLastUncommittedChangeEvent() != null) {
        final BsonDocument ceDoc = ChangeEvent.toBsonDocument(getLastUncommittedChangeEvent());
        final OutputBuffer outputBuffer = new BasicOutputBuffer();
        final BsonWriter innerWriter = new BsonBinaryWriter(outputBuffer);
        new BsonDocumentCodec().encode(innerWriter, ceDoc, EncoderContext.builder().build());
        final BsonBinary encoded = new BsonBinary(outputBuffer.toByteArray());
        // TODO: This may put the doc above the 16MiB but ignore for now.
        asDoc.put(ConfigCodec.Fields.LAST_UNCOMMITTED_CHANGE_EVENT, encoded);
      }
      final BsonArray committedVersions = new BsonArray(new ArrayList<>(this.committedVersions));
      asDoc.put(ConfigCodec.Fields.COMMITTED_VERSIONS, committedVersions);
      asDoc.put(ConfigCodec.Fields.IS_STALE, new BsonBoolean(isStale));
      asDoc.put(ConfigCodec.Fields.IS_FROZEN, new BsonBoolean(isFrozen));
      return asDoc;
    } finally {
      docLock.readLock().unlock();
    }
  }

  static CoreDocumentSynchronizationConfig fromBsonDocument(final BsonDocument document) {
    keyPresent(ConfigCodec.Fields.DOCUMENT_ID_FIELD, document);
    keyPresent(ConfigCodec.Fields.NAMESPACE_FIELD, document);
    keyPresent(ConfigCodec.Fields.SCHEMA_VERSION_FIELD, document);
    keyPresent(ConfigCodec.Fields.LAST_RESOLUTION_FIELD, document);
    keyPresent(ConfigCodec.Fields.COMMITTED_VERSIONS, document);
    keyPresent(ConfigCodec.Fields.IS_STALE, document);

    final int schemaVersion =
        document.getNumber(ConfigCodec.Fields.SCHEMA_VERSION_FIELD).intValue();
    if (schemaVersion != 1) {
      throw new IllegalStateException(
          String.format(
              "unexpected schema version '%d' for %s",
              schemaVersion,
              CoreDocumentSynchronizationConfig.class.getSimpleName()));
    }

    final MongoNamespace namespace =
        new MongoNamespace(document.getString(ConfigCodec.Fields.NAMESPACE_FIELD).getValue());
    final BsonArray committedVersionsArr = document.getArray(ConfigCodec.Fields.COMMITTED_VERSIONS);
    final Set<BsonValue> committedVersions = new HashSet<>(committedVersionsArr);

    final BsonValue lastVersion;
    if (document.containsKey(ConfigCodec.Fields.LAST_KNOWN_REMOTE_VERSION_FIELD)) {
      lastVersion = document.get(ConfigCodec.Fields.LAST_KNOWN_REMOTE_VERSION_FIELD);
    } else {
      lastVersion = null;
    }

    final ChangeEvent<BsonDocument> lastUncommittedChangeEvent;
    if (document.containsKey(ConfigCodec.Fields.LAST_UNCOMMITTED_CHANGE_EVENT)) {
      final BsonBinary eventBin =
          document.getBinary(ConfigCodec.Fields.LAST_UNCOMMITTED_CHANGE_EVENT);
      final BsonReader innerReader = new BsonBinaryReader(ByteBuffer.wrap(eventBin.getData()));
      lastUncommittedChangeEvent =
          ChangeEvent.changeEventCoder.decode(innerReader, DecoderContext.builder().build());
    } else {
      lastUncommittedChangeEvent = null;
    }

    return new CoreDocumentSynchronizationConfig(
        namespace,
        document.get(ConfigCodec.Fields.DOCUMENT_ID_FIELD),
        lastUncommittedChangeEvent,
        document.getNumber(ConfigCodec.Fields.LAST_RESOLUTION_FIELD).longValue(),
        lastVersion,
        committedVersions,
        document.getBoolean(ConfigCodec.Fields.IS_STALE).getValue(),
        document.getBoolean(ConfigCodec.Fields.IS_FROZEN, new BsonBoolean(false)).getValue());
  }

  static final ConfigCodec configCodec = new ConfigCodec();

  static final class ConfigCodec implements Codec<CoreDocumentSynchronizationConfig> {

    @Override
    public CoreDocumentSynchronizationConfig decode(
        final BsonReader reader,
        final DecoderContext decoderContext
    ) {
      final BsonDocument document = (new BsonDocumentCodec()).decode(reader, decoderContext);
      return fromBsonDocument(document);
    }

    @Override
    public void encode(
        final BsonWriter writer,
        final CoreDocumentSynchronizationConfig value,
        final EncoderContext encoderContext
    ) {
      new BsonDocumentCodec().encode(writer, value.toBsonDocument(), encoderContext);
    }

    @Override
    public Class<CoreDocumentSynchronizationConfig> getEncoderClass() {
      return CoreDocumentSynchronizationConfig.class;
    }

    static class Fields {
      static final String DOCUMENT_ID_FIELD = "document_id";
      static final String SCHEMA_VERSION_FIELD = "schema_version";
      static final String NAMESPACE_FIELD = "namespace";
      static final String LAST_RESOLUTION_FIELD = "last_resolution";
      static final String LAST_KNOWN_REMOTE_VERSION_FIELD = "last_known_remote_version";
      static final String LAST_UNCOMMITTED_CHANGE_EVENT = "last_uncommitted_change_event";
      static final String COMMITTED_VERSIONS = "committed_versions";
      static final String IS_STALE = "is_stale";
      static final String IS_FROZEN = "is_frozen";
    }
  }
}
