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

import static com.mongodb.stitch.core.services.mongodb.remote.sync.internal.DataSynchronizer.DOCUMENT_VERSION_FIELD;

import com.mongodb.stitch.core.internal.common.BsonUtils;
import javax.annotation.Nullable;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.bson.BsonValue;

import java.util.UUID;

public final class DocumentVersionInfo {
  private final int syncProtocolVersion;
  private final String instanceId;
  private final long versionCounter;


  static final String SYNC_PROTOCOL_VERSION_FIELD = "spv";
  static final String INSTANCE_ID_FIELD = "id";
  static final String VERSION_COUNTER_FIELD = "v";

  private final BsonDocument filter;
  private final BsonDocument versionDoc;

  private DocumentVersionInfo(
          final BsonDocument version,
          final BsonValue documentId,
          final BsonDocument versionForFilter
  ) {
    if (version != null) {
      this.versionDoc = version;
      this.syncProtocolVersion = versionDoc.getInt32(SYNC_PROTOCOL_VERSION_FIELD).getValue();
      this.instanceId = versionDoc.getString(INSTANCE_ID_FIELD).getValue();
      this.versionCounter = versionDoc.getInt64(VERSION_COUNTER_FIELD).getValue();
    } else {
      this.versionDoc = null;

      this.syncProtocolVersion = -1;
      this.instanceId = null;
      this.versionCounter = -1;
    }

    this.filter = getVersionedFilter(documentId, versionForFilter);
  }

//  private DocumentVersionInfo(
//          final BsonValue documentId,
//          final int syncProtocolVersion,
//          final String instanceId,
//          final long versionCounter
//  ) {
//    this.syncProtocolVersion = syncProtocolVersion;
//    this.instanceId = instanceId;
//    this.versionCounter = versionCounter;
//
//    this.versionDoc = constructVersionDoc();
//    this.filter = getVersionedFilter(documentId, versionDoc);
//  }

  public BsonDocument getVersionDoc() {
    return versionDoc;
  }

//  private BsonDocument constructVersionDoc() {
//    BsonDocument versionDoc = new BsonDocument();
//
//    versionDoc.append(SYNC_PROTOCOL_VERSION_FIELD, new BsonInt32(syncProtocolVersion));
//    versionDoc.append(INSTANCE_ID_FIELD, new BsonString(instanceId));
//    versionDoc.append(VERSION_COUNTER_FIELD, new BsonInt64(versionCounter));
//
//    return versionDoc;
//  }

  public int getSyncProtocolVersion() {
    return syncProtocolVersion;
  }

  public String getInstanceId() {
    return instanceId;
  }

  public long getVersionCounter() {
    return versionCounter;
  }

//  public DocumentVersionInfo withIncrementedVersionCounter() {
//    return new DocumentVersionInfo(
//            this.documentId,
//            this.syncProtocolVersion,
//            this.instanceId,
//            this.versionCounter + 1
//    );
//  }
//
//  public DocumentVersionInfo withNewInstanceId() {
//    return new DocumentVersionInfo(
//            this.documentId,
//            this.syncProtocolVersion,
//            UUID.randomUUID().toString(),
//            0
//    );
//  }

  public BsonDocument getFilter() {
    return filter;
  }

  static DocumentVersionInfo getLocalVersionInfo(
      final CoreDocumentSynchronizationConfig docConfig,
      final BsonDocument localDocument
  ) {
    final BsonDocument version = getDocumentVersion(localDocument);
    return new DocumentVersionInfo(
        version, docConfig.getDocumentId(), docConfig.getLastKnownRemoteVersion()
    );
  }

  static DocumentVersionInfo getRemoteVersionInfo(final BsonDocument remoteDocument) {
    final BsonDocument version = getDocumentVersion(remoteDocument);
    return new DocumentVersionInfo(version, BsonUtils.getDocumentId(remoteDocument), version);
  }

//  public static DocumentVersionInfo createFreshDocumentVersion(BsonDocument document) {
//    return new DocumentVersionInfo(
//            BsonUtils.getDocumentId(document),
//            1,
//            UUID.randomUUID().toString(),
//            0
//    );
//  }

  public static BsonDocument getFreshVersionDocument() {
    BsonDocument versionDoc = new BsonDocument();

    versionDoc.append(SYNC_PROTOCOL_VERSION_FIELD, new BsonInt32(1));
    versionDoc.append(INSTANCE_ID_FIELD, new BsonString(UUID.randomUUID().toString()));
    versionDoc.append(VERSION_COUNTER_FIELD, new BsonInt64(0));

    return versionDoc;
  }

  public static BsonDocument withIncrementedVersionCounter(BsonDocument versionDoc) {
    BsonDocument newVersionDoc = new BsonDocument();

    newVersionDoc.put(SYNC_PROTOCOL_VERSION_FIELD, versionDoc.get(SYNC_PROTOCOL_VERSION_FIELD));
    newVersionDoc.put(INSTANCE_ID_FIELD, versionDoc.get(INSTANCE_ID_FIELD));
    newVersionDoc.put(
            VERSION_COUNTER_FIELD,
            new BsonInt64(versionDoc.getInt64(VERSION_COUNTER_FIELD).getValue() + 1L)
    );

    return newVersionDoc;
  }

  /**
   * Returns the version of the given document, if any; returns null otherwise.
   * @param document the document to get the version from.
   * @return the version of the given document, if any; returns null otherwise.
   */
  static BsonDocument getDocumentVersion(final BsonDocument document) {
    if (document == null || !document.containsKey(DOCUMENT_VERSION_FIELD)) {
      return null;
    }
    return document.getDocument(DOCUMENT_VERSION_FIELD, null);
  }

  /**
   * Returns a query filter for the given document _id and version. The version is allowed to be
   * null. The query will match only if there is either no version on the document in the database
   * in question if we have no reference of the version or if the version matches the database's
   * version.
   *
   * @param documentId the _id of the document.
   * @param version the expected version of the document, if any.
   * @return a query filter for the given document _id and version for a remote operation.
   */
  static BsonDocument getVersionedFilter(
      final BsonValue documentId,
      @Nullable final BsonValue version
  ) {
    final BsonDocument filter = new BsonDocument("_id", documentId);
    if (version == null) {
      filter.put(DOCUMENT_VERSION_FIELD, new BsonDocument("$exists", BsonBoolean.FALSE));
    } else {
      filter.put(DOCUMENT_VERSION_FIELD, version);
    }
    return filter;
  }
}
