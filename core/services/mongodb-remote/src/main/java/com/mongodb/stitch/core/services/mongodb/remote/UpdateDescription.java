/*
 * Copyright 2018-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.stitch.core.services.mongodb.remote;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonElement;
import org.bson.BsonValue;

import static com.mongodb.stitch.core.services.mongodb.remote.sync.internal.DataSynchronizer.DOCUMENT_VERSION_FIELD;

public final class UpdateDescription {
    private final BsonDocument updatedFields;
    private final Collection<String> removedFields;

    UpdateDescription(
        final BsonDocument updatedFields,
        final Collection<String> removedFields
    ) {
      this.updatedFields = updatedFields == null ? new BsonDocument() : updatedFields;
      this.removedFields = removedFields == null ? Collections.<String>emptyList() : removedFields;
    }

    public BsonDocument getUpdatedFields() {
      return updatedFields;
    }

    public Collection<String> getRemovedFields() {
      return removedFields;
    }

    /**
     * Convert this update description to an update document.
     * @return an update document with the appropriate $set and $unset
     *         documents
     */
    BsonDocument toUpdateDocument() {
      final List<BsonElement> unsets = new ArrayList<>();
      for (final String removedField : this.removedFields) {
        unsets.add(new BsonElement(removedField, new BsonBoolean(true)));
      }
      final BsonDocument updateDocument = new BsonDocument();

      if (this.updatedFields.size() > 0) {
        updateDocument.append("$set", this.updatedFields);
      }

      if (unsets.size() > 0) {
        updateDocument.append("$unset", new BsonDocument(unsets));
      }

      return updateDocument;
    }

    /**
     * Find the diff between two documents.
     *
     * NOTE: This does not do a full diff on {@link BsonArray}. If there is
     * an inequality between the old and new array, the old array will
     * simply be replaced by the new one.
     *
     * @param beforeDocument original document
     * @param afterDocument document to diff on
     * @param onKey the key for our depth level
     * @param updatedFields contiguous document of updated fields,
     *                      nested or otherwise
     * @param removedFields contiguous list of removedFields,
     *                      nested or otherwise
     * @return a description of the updated fields and removed keys between
     *         the documents
     */
    private static UpdateDescription diff(final @Nonnull BsonDocument beforeDocument,
                                          final @Nonnull BsonDocument afterDocument,
                                          final @Nullable String onKey,
                                          final BsonDocument updatedFields,
                                          final List<String> removedFields) {
      // for each key in this document...
      for (final Map.Entry<String, BsonValue> entry: beforeDocument.entrySet()) {
        final String key = entry.getKey();
        // don't worry about the _id or version field for now
        if (key.equals("_id") || key.equals(DOCUMENT_VERSION_FIELD)) {
          continue;
        }
        final BsonValue oldValue = entry.getValue();

        final String actualKey = onKey == null ? key : String.format("%s.%s", onKey, key);
        // if the key exists in the other document AND both are BsonDocuments
        // diff the documents recursively, carrying over the keys to keep
        // updatedFields and removedFields flat.
        // this will allow us to reference whole objects as well as nested
        // properties.
        // else if the key does not exist, the key has been removed.
        if (afterDocument.containsKey(key)) {
          final BsonValue newValue = afterDocument.get(key);
          if (oldValue instanceof BsonDocument && newValue instanceof BsonDocument) {
            diff((BsonDocument) oldValue,
                (BsonDocument) newValue,
                actualKey,
                updatedFields,
                removedFields);
          } else if (!oldValue.equals(newValue)) {
            updatedFields.put(actualKey, newValue);
          }
        } else {
          removedFields.add(actualKey);
        }
      }

      // for each key in the other document...
      for (final Map.Entry<String, BsonValue> entry: afterDocument.entrySet()) {
        final String key = entry.getKey();
        // don't worry about the _id or version field for now
        if (key.equals("_id") || key.equals(DOCUMENT_VERSION_FIELD)) {
          continue;
        }

        final BsonValue newValue = entry.getValue();
        // if the key is not in the this document,
        // it is a new key with a new value.
        // updatedFields will included keys that must
        // be newly created.
        final String actualKey = onKey == null ? key : String.format("%s.%s", onKey, key);
        if (!beforeDocument.containsKey(key)) {
          updatedFields.put(actualKey, newValue);
        }
      }

      return new UpdateDescription(updatedFields, removedFields);
    }

    /**
     * Find the diff between two documents.
     *
     * NOTE: This does not do a full diff on [BsonArray]. If there is
     * an inequality between the old and new array, the old array will
     * simply be replaced by the new one.
     *
     * @param beforeDocument original document
     * @param afterDocument document to diff on
     * @return a description of the updated fields and removed keys between
     *         the documents
     */
    static UpdateDescription diff(@Nullable final BsonDocument beforeDocument,
                                  @Nullable final BsonDocument afterDocument) {
      if (beforeDocument == null || afterDocument == null) {
        return new UpdateDescription(new BsonDocument(), new ArrayList<>());
      }

      return UpdateDescription.diff(
          beforeDocument,
          afterDocument,
          null,
          new BsonDocument(),
          new ArrayList<>()
      );
    }
  }
}
