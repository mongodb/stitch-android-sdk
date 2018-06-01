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

import static com.mongodb.stitch.core.services.mongodb.sync.internal.DataSynchronizer.DOCUMENT_VERSION_FIELD;

import com.mongodb.stitch.core.internal.common.BsonUtils;
import javax.annotation.Nullable;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonValue;

final class DocumentVersionInfo {
  private final BsonValue version;
  private final BsonDocument filter;

  private DocumentVersionInfo(final BsonValue version, final BsonDocument filter) {
    this.version = version;
    this.filter = filter;
  }

  public BsonValue getCurrentVersion() {
    return version;
  }

  public BsonDocument getFilter() {
    return filter;
  }

  static DocumentVersionInfo getLocalVersionInfo(
      final CoreDocumentSynchronizationConfig docConfig,
      final BsonDocument localDocument
  ) {
    final BsonValue version = getDocumentVersion(localDocument);
    return new DocumentVersionInfo(
        version,
        getVersionedFilter(
            docConfig.getDocumentId(), docConfig.getLastKnownRemoteVersion()));
  }

  static DocumentVersionInfo getRemoteVersionInfo(final BsonDocument remoteDocument) {
    final BsonValue version = getDocumentVersion(remoteDocument);
    return new DocumentVersionInfo(
        version,
        getVersionedFilter(
            BsonUtils.getDocumentId(remoteDocument), version));
  }

  /**
   * Returns the version of the given document, if any; returns null otherwise.
   * @param document the document to get the version from.
   * @return the version of the given document, if any; returns null otherwise.
   */
  static BsonValue getDocumentVersion(final BsonDocument document) {
    if (document == null || !document.containsKey(DOCUMENT_VERSION_FIELD)) {
      return null;
    }
    return document.get(DOCUMENT_VERSION_FIELD);
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
