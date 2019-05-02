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

package com.mongodb.stitch.core.services.mongodb.remote;

import static com.mongodb.stitch.core.internal.common.Assertions.keyPresent;

import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.DocumentVersionInfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.bson.BsonValue;

/**
 * Represents a change event communicated via a MongoDB change stream from
 * Stitch when watchCompact is called. These change events omit full documents
 * from the event for updates, as well as other fields that are unnecessary in
 * the context of Stitch.
 *
 * @param <DocumentT> The underlying type of document for which this change event was produced.
 */
public final class CompactChangeEvent<DocumentT> extends BaseChangeEvent<DocumentT> {
  private final @Nullable DocumentVersionInfo.Version stitchDocumentVersion;
  private final @Nullable Long stitchDocumentHash;

  /**
   * Constructs a change event.
   *
   * @param operationType The operation type represented by the change event.
   * @param fullDocument The full document of this change event (for inserts and updates)
   * @param documentKey The id if the underlying document that changed.
   * @param updateDescription The description of what has changed (for updates only).
   * @param stitchDocumentVersion The Stitch sync version of this document at the time of the event.
   * @param stitchDocumentHash The Stitch sync hash of this document at the time of the event.
   * @param hasUncommittedWrites Whether this represents a local uncommitted write.
   */
  public CompactChangeEvent(
      final @Nonnull OperationType operationType,
      final @Nullable DocumentT fullDocument,
      final @Nonnull BsonDocument documentKey,
      final @Nullable UpdateDescription updateDescription,
      final @Nullable DocumentVersionInfo.Version stitchDocumentVersion,
      final @Nullable Long stitchDocumentHash,
      final boolean hasUncommittedWrites
  ) {
    super(operationType, fullDocument, documentKey, updateDescription, hasUncommittedWrites);

    this.stitchDocumentVersion = stitchDocumentVersion;
    this.stitchDocumentHash = stitchDocumentHash;
  }

  /**
   * Returns the MongoDB Mobile Sync version of the document after this event, if it exists on the
   * document after the update.
   *
   * @return the sync document version
   */
  public @Nullable DocumentVersionInfo.Version getStitchDocumentVersion() {
    return stitchDocumentVersion;
  }

  /**
   * Returns the MongoDB Mobile Sync version info of the document after this event.
   *
   * @return the sync document version info
   */
  public @Nonnull DocumentVersionInfo getStitchDocumentVersionInfo() {
    return new DocumentVersionInfo(
        stitchDocumentVersion,
        getDocumentKey().get("_id")
    );
  }

  /**
   * Returns the FNV-1a hash of the document as computed after the rules were applied, and after
   * the document version was removed from the document.
   *
   * @return the sync document hash
   */
  public @Nullable Long getStitchDocumentHash() {
    return stitchDocumentHash;
  }

  /**
   * Creates a copy of this change event with uncommitted writes flag set to false.
   *
   * @return new change event without uncommitted writes flag
   */
  public CompactChangeEvent<DocumentT> withoutUncommittedWrites() {
    return new CompactChangeEvent<>(
        this.getOperationType(),
        this.getFullDocument(),
        this.getDocumentKey(),
        this.getUpdateDescription(),
        this.getStitchDocumentVersion(),
        this.getStitchDocumentHash(),
        false);
  }

  /**
   * Serializes this change event into a {@link BsonDocument}.
   * @return the serialized document.
   */
  public BsonDocument toBsonDocument() {
    final BsonDocument asDoc = new BsonDocument();
    asDoc.put(Fields.OPERATION_TYPE_FIELD, new BsonString(getOperationType().toRemote()));
    asDoc.put(Fields.DOCUMENT_KEY_FIELD, getDocumentKey());

    if (getFullDocument() != null && (getFullDocument() instanceof BsonValue)
        && ((BsonValue) getFullDocument()).isDocument()) {
      asDoc.put(Fields.FULL_DOCUMENT_FIELD, (BsonValue) getFullDocument());
    }

    if (getUpdateDescription() != null) {
      asDoc.put(Fields.UPDATE_DESCRIPTION_FIELD, getUpdateDescription().toBsonDocument());
    }

    if (stitchDocumentVersion != null) {
      asDoc.put(Fields.STITCH_DOCUMENT_VERSION_FIELD, stitchDocumentVersion.toBsonDocument());
    }

    if (stitchDocumentHash != null) {
      asDoc.put(Fields.STITCH_DOCUMENT_HASH_FIELD, new BsonInt64(stitchDocumentHash));
    }

    asDoc.put(Fields.WRITE_PENDING_FIELD, new BsonBoolean(hasUncommittedWrites()));
    return asDoc;
  }

  /**
   * Deserializes a {@link BsonDocument} into an instance of change event.
   * @param document the serialized document
   * @return the deserialized change event
   */
  public static CompactChangeEvent fromBsonDocument(final BsonDocument document) {
    keyPresent(Fields.OPERATION_TYPE_FIELD, document);
    keyPresent(Fields.DOCUMENT_KEY_FIELD, document);

    final BsonDocument fullDocument;
    if (document.containsKey(Fields.FULL_DOCUMENT_FIELD)) {
      final BsonValue fdVal = document.get(Fields.FULL_DOCUMENT_FIELD);
      if (fdVal.isDocument()) {
        fullDocument = fdVal.asDocument();
      } else {
        fullDocument = null;
      }
    } else {
      fullDocument = null;
    }

    final UpdateDescription updateDescription;
    if (document.containsKey(Fields.UPDATE_DESCRIPTION_FIELD)) {
      updateDescription = UpdateDescription.fromBsonDocument(
          document.getDocument(Fields.UPDATE_DESCRIPTION_FIELD)
      );
    } else {
      updateDescription = null;
    }

    final DocumentVersionInfo.Version stitchDocumentVersion;
    if (document.containsKey(Fields.STITCH_DOCUMENT_VERSION_FIELD)) {
      stitchDocumentVersion = DocumentVersionInfo.Version
          .fromBsonDocument(document.getDocument(Fields.STITCH_DOCUMENT_VERSION_FIELD));
    } else {
      stitchDocumentVersion = null;
    }

    final Long stitchDocumentHash;
    if (document.containsKey(Fields.STITCH_DOCUMENT_HASH_FIELD)) {
      stitchDocumentHash = document.getInt64(
          Fields.STITCH_DOCUMENT_HASH_FIELD
      ).getValue();
    } else {
      stitchDocumentHash = null;
    }

    return new CompactChangeEvent<>(
        OperationType.fromRemote(document.getString(Fields.OPERATION_TYPE_FIELD).getValue()),
        fullDocument,
        document.getDocument(Fields.DOCUMENT_KEY_FIELD),
        updateDescription,
        stitchDocumentVersion,
        stitchDocumentHash,
        document.getBoolean(Fields.WRITE_PENDING_FIELD, BsonBoolean.FALSE).getValue());
  }

  private static final class Fields {
    static final String OPERATION_TYPE_FIELD = "ot";
    static final String FULL_DOCUMENT_FIELD = "fd";
    static final String DOCUMENT_KEY_FIELD = "dk";

    static final String UPDATE_DESCRIPTION_FIELD = "ud";

    static final String STITCH_DOCUMENT_VERSION_FIELD = "sdv";
    static final String STITCH_DOCUMENT_HASH_FIELD = "sdh";

    static final String WRITE_PENDING_FIELD = "writePending";
  }
}
