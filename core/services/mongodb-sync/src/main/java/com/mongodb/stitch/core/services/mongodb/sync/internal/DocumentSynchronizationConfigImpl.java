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

import com.mongodb.stitch.core.services.mongodb.sync.DocumentSynchronizationConfig;
import com.mongodb.stitch.core.services.mongodb.sync.SyncConflictResolver;
import javax.annotation.Nullable;
import org.bson.BsonValue;

class DocumentSynchronizationConfigImpl implements DocumentSynchronizationConfig {

  private final BsonValue documentId;
  private final SyncConflictResolver conflictResolver;
  private final boolean hasUncommittedWrites;

  DocumentSynchronizationConfigImpl(final CoreDocumentSynchronizationConfig config) {
    this.documentId = config.getDocumentId();
    this.conflictResolver = config.getConflictResolver();
    this.hasUncommittedWrites = config.hasPendingWrites();
  }

  /**
   * Returns the _id of the document being synchronized;
   *
   * @return the _id of the document being synchronized;
   */
  public BsonValue getDocumentId() {
    return documentId;
  }

  /**
   * Returns the conflict resolver for this document, if any.
   *
   * @return the conflict resolver for this document.
   */
  @Nullable
  public SyncConflictResolver getConflictResolver() {
    return conflictResolver;
  }

  /**
   * Returns whether or not this document has pending writes that have not yet been committed
   * remotely.
   *
   * @return whether or not this document has pending writes that have not yet been committed
   *         remotely.
   */
  public boolean hasPendingWrites() {
    return hasUncommittedWrites;
  }

  // Equality on documentId
  @Override
  public boolean equals(final Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof DocumentSynchronizationConfigImpl)) {
      return false;
    }
    final DocumentSynchronizationConfigImpl other = (DocumentSynchronizationConfigImpl) object;
    return getDocumentId().equals(other.getDocumentId());
  }

  // Hash on documentId
  @Override
  public int hashCode() {
    return super.hashCode()
        + getDocumentId().hashCode();
  }
}
