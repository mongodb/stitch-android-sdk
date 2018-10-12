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

import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.bson.BsonValue;


final class DocumentVersionInfo {
  @Nullable private final Version version;
  @Nullable private final BsonDocument versionDoc;
  @Nullable private final BsonDocument filter;

  private static class Fields {
    private static final String SYNC_PROTOCOL_VERSION_FIELD = "spv";
    private static final String INSTANCE_ID_FIELD = "id";
    private static final String VERSION_COUNTER_FIELD = "v";
  }

  static class Version {
    final int syncProtocolVersion;
    final String instanceId;
    final long versionCounter;

    Version(
            final int syncProtocolVersion,
            final String instanceId,
            final long versionCounter) {
      this.syncProtocolVersion = syncProtocolVersion;
      this.instanceId = instanceId;
      this.versionCounter = versionCounter;
    }

    /**
     * Returns the synchronization protocol version of this version.
     * @return an int representing the synchronization protocol version of this version.
     */
    int getSyncProtocolVersion() {
      return syncProtocolVersion;
    }

    /**
     * Returns the GUID instance id of this version.
     * @return a String representing the instance id of this version.
     */
    String getInstanceId() {
      return instanceId;
    }

    /**
     * Returns the version counter of this version.
     * @return a long representing the version counter of this version.
     */
    long getVersionCounter() {
      return versionCounter;
    }
  }

  private DocumentVersionInfo(
          @Nullable final BsonDocument version,
          @Nullable final BsonValue documentId
  ) {
    if (version != null) {
      this.versionDoc = version;
      this.version = new Version(
        versionDoc.getInt32(Fields.SYNC_PROTOCOL_VERSION_FIELD).getValue(),
        versionDoc.getString(Fields.INSTANCE_ID_FIELD).getValue(),
        versionDoc.getInt64(Fields.VERSION_COUNTER_FIELD).getValue()
      );
    } else {
      this.versionDoc = null;
      this.version = null;
    }

    if (documentId != null) {
      this.filter = getVersionedFilter(documentId, version);
    } else {
      this.filter = null;
    }
  }

  @Nullable BsonDocument getVersionDoc() {
    return versionDoc;
  }

  /**
   * Returns whether this version is non-empty (i.e. a version from a document with no version)
   * @return true if the version is non-empty, false if the version is empty.
   */
  boolean hasVersion() {
    return version != null;
  }

  /**
   * Returns the concrete version values of this version info object. Will throw an
   * IllegalStateException if the version info has no version (i.e if hasVersion would return
   * false)
   * @return a Version representing the concrete version values of this info object.
   */
  @Nonnull Version getVersion() {
    if (this.version == null) {
      throw new IllegalStateException(
              "Attempting to access sync version information on a document with no version."
      );
    }

    return this.version;
  }

  /**
   * Gets a filter that will only return the document in a query if it matches the current version.
   * Will return null if a document ID was not specified when the version info was constructed.
   *
   * @return a BsonDocument representing the filter to request a document at this version
   */
  @Nullable BsonDocument getFilter() {
    return filter;
  }

  /**
   * Returns the current version info for a locally synchronized document.
   * @param docConfig the CoreDocumentSynchronizationConfig to get the version info from.
   * @return a DocumentVersionInfo
   */
  static DocumentVersionInfo getLocalVersionInfo(
      final CoreDocumentSynchronizationConfig docConfig
  ) {
    return new DocumentVersionInfo(
        docConfig.getLastKnownRemoteVersion(),
        docConfig.getDocumentId()
    );
  }

  /**
   * Returns the current version info for a provided remote document.
   * @param remoteDocument the remote BSON document from which to extract version info
   * @return a DocumentVersionInfo
   */
  static DocumentVersionInfo getRemoteVersionInfo(final BsonDocument remoteDocument) {
    final BsonDocument version = getDocumentVersionDoc(remoteDocument);
    return new DocumentVersionInfo(
            version,
            remoteDocument != null
                    ? BsonUtils.getDocumentId(remoteDocument, null) : null
    );
  }

  /**
   * Returns a DocumentVersionInfo constructed from a raw version document. The returned
   * DocumentVersionInfo will have no document ID specified, so it will always return a null
   * filter if the filter is requested.
   * @param versionDoc the raw version document from which to extract version info
   * @return a DocumentVersionInfo
   */
  static DocumentVersionInfo fromVersionDoc(final BsonDocument versionDoc) {
    return new DocumentVersionInfo(versionDoc, null);
  }

  /**
   * Returns a BSON version document representing a new version with a new instance ID, and
   * version counter of zero.
   * @return a BsonDocument representing a synchronization version
   */
  static BsonDocument getFreshVersionDocument() {
    final BsonDocument versionDoc = new BsonDocument();

    versionDoc.append(Fields.SYNC_PROTOCOL_VERSION_FIELD, new BsonInt32(1));
    versionDoc.append(Fields.INSTANCE_ID_FIELD, new BsonString(UUID.randomUUID().toString()));
    versionDoc.append(Fields.VERSION_COUNTER_FIELD, new BsonInt64(0L));

    return versionDoc;
  }

  /**
   * Returns the version document of the given document, if any; returns null otherwise.
   * @param document the document to get the version from.
   * @return the version of the given document, if any; returns null otherwise.
   */
  static BsonDocument getDocumentVersionDoc(final BsonDocument document) {
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
      @Nonnull final BsonValue documentId,
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

  /**
   * Given a DocumentVersionInfo, returns a BSON document representing the next version. This means
   * and incremented version count for a non-empty version, or a fresh version document for an
   * empty version.
   * @return a BsonDocument representing a synchronization version
   */
  BsonDocument getNextVersion() {
    if (!this.hasVersion() || this.getVersionDoc() == null) {
      return getFreshVersionDocument();
    }
    final BsonDocument nextVersion = BsonUtils.copyOfDocument(this.getVersionDoc());
    nextVersion.put(
            Fields.VERSION_COUNTER_FIELD,
            new BsonInt64(this.getVersion().getVersionCounter() + 1));
    return nextVersion;
  }
}
