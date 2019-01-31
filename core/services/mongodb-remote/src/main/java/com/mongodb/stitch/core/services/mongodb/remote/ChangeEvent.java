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

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.services.mongodb.remote.internal.ResultDecoders;

import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;

import static com.mongodb.stitch.core.internal.common.Assertions.keyPresent;

/**
 * Represents a change event communicated via a MongoDB change stream.
 *
 * @param <DocumentT> The underlying type of document for which this change event was produced.
 */
// TODO: Should there be a local and remote type for the pending part?
public final class ChangeEvent<DocumentT> {
  private final BsonDocument id; // Metadata related to the operation (the resumeToken).
  private final OperationType operationType;
  private final DocumentT fullDocument;
  private final MongoNamespace ns;
  private final BsonDocument documentKey;
  private final UpdateDescription updateDescription;
  private final boolean hasUncommittedWrites;

  /**
   * Constructs a change event.
   *
   * @param id The id of the change event.
   * @param operationType The operation type represented by the change event.
   * @param fullDocument The full document at some point after the change is applied.
   * @param ns The namespace (database and collection) of the document.
   * @param documentKey The id if the underlying document that changed.
   * @param updateDescription The description of what has changed (for updates only).
   * @param hasUncommittedWrites Whether this represents a local uncommitted write.
   */
  public ChangeEvent(
      final BsonDocument id,
      final OperationType operationType,
      final DocumentT fullDocument,
      final MongoNamespace ns,
      final BsonDocument documentKey,
      final UpdateDescription updateDescription,
      final boolean hasUncommittedWrites
  ) {
    this.id = id;
    this.operationType = operationType;
    this.fullDocument = fullDocument;
    this.ns = ns;
    this.documentKey = documentKey;
    this.updateDescription = updateDescription == null
        ? new UpdateDescription(null, null) : updateDescription;
    this.hasUncommittedWrites = hasUncommittedWrites;
  }

  /**
   * Returns the ID of the change event itself.
   *
   * @return the id of this change event.
   */
  public BsonDocument getId() {
    return id;
  }

  /**
   * Returns the operation type of the change that triggered the change event.
   *
   * @return the operation type of this change event.
   */
  public OperationType getOperationType() {
    return operationType;
  }

  /**
   * The full document at some point after the change has been applied.
   *
   * @return the full document.
   */
  public DocumentT getFullDocument() {
    return fullDocument;
  }

  /**
   * The namespace the change relates to.
   *
   * @return the namespace.
   */
  public MongoNamespace getNamespace() {
    return ns;
  }

  /**
   * The unique identifier for the document that was actually changed.
   *
   * @return the document key.
   */
  public BsonDocument getDocumentKey() {
    return documentKey;
  }

  /**
   * In the case of an update, the description of which fields have been added, removed or updated.
   *
   * @return the update description.
   */
  public UpdateDescription getUpdateDescription() {
    return updateDescription;
  }

  /**
   * Indicates a local change event that has not yet been synchronized with a remote data store.
   * Used only for the sync use case.
   *
   * @return whether or not this change event represents uncommitted writes.
   */
  public boolean hasUncommittedWrites() {
    return hasUncommittedWrites;
  }

  /**
   * Serializes this change event into a {@link BsonDocument}.
   * @return the serialized document.
   */
  public BsonDocument toBsonDocument() {
    final BsonDocument asDoc = new BsonDocument();
    asDoc.put(Fields.ID_FIELD, id);
    asDoc.put(Fields.OPERATION_TYPE_FIELD,
        new BsonString(operationType.toRemote()));
    final BsonDocument nsDoc = new BsonDocument();
    nsDoc.put(Fields.NS_DB_FIELD,
        new BsonString(ns.getDatabaseName()));
    nsDoc.put(Fields.NS_COLL_FIELD,
        new BsonString(getNamespace().getCollectionName()));
    asDoc.put(Fields.NS_FIELD, nsDoc);
    asDoc.put(Fields.DOCUMENT_KEY_FIELD, documentKey);
    if (fullDocument != null && (fullDocument instanceof BsonValue)
        && ((BsonValue)fullDocument).isDocument()) {
      asDoc.put(Fields.FULL_DOCUMENT_FIELD, (BsonValue)fullDocument);
    }
    if (updateDescription != null) {
      final BsonDocument updateDescDoc = new BsonDocument();
      updateDescDoc.put(
          Fields.UPDATE_DESCRIPTION_UPDATED_FIELDS_FIELD,
          updateDescription.getUpdatedFields());

      final BsonArray removedFields = new BsonArray();
      for (final String field : updateDescription.getRemovedFields()) {
        removedFields.add(new BsonString(field));
      }
      updateDescDoc.put(
          Fields.UPDATE_DESCRIPTION_REMOVED_FIELDS_FIELD,
          removedFields);
      asDoc.put(Fields.UPDATE_DESCRIPTION_FIELD, updateDescDoc);
    }
    asDoc.put(Fields.WRITE_PENDING_FIELD,
        new BsonBoolean(hasUncommittedWrites));
    return asDoc;
  }

  /**
   * Deserializes a {@link BsonDocument} into an instance of change event.
   * @param document the serialized document
   * @return the deserialized change event
   */
  public static ChangeEvent fromBsonDocument(final BsonDocument document) {
    keyPresent(Fields.ID_FIELD, document);
    keyPresent(Fields.OPERATION_TYPE_FIELD, document);
    keyPresent(Fields.NS_FIELD, document);
    keyPresent(Fields.DOCUMENT_KEY_FIELD, document);

    final BsonDocument nsDoc = document.getDocument(Fields.NS_FIELD);
    final UpdateDescription updateDescription;
    if (document.containsKey(Fields.UPDATE_DESCRIPTION_FIELD)) {
      final BsonDocument updateDescDoc =
          document.getDocument(Fields.UPDATE_DESCRIPTION_FIELD);
      keyPresent(Fields.UPDATE_DESCRIPTION_UPDATED_FIELDS_FIELD, updateDescDoc);
      keyPresent(Fields.UPDATE_DESCRIPTION_REMOVED_FIELDS_FIELD, updateDescDoc);

      final BsonArray removedFieldsArr =
          updateDescDoc.getArray(Fields.UPDATE_DESCRIPTION_REMOVED_FIELDS_FIELD);
      final Collection<String> removedFields = new ArrayList<>(removedFieldsArr.size());
      for (final BsonValue field : removedFieldsArr) {
        removedFields.add(field.asString().getValue());
      }
      updateDescription = new UpdateDescription(updateDescDoc.getDocument(
          Fields.UPDATE_DESCRIPTION_UPDATED_FIELDS_FIELD),
          removedFields);
    } else {
      updateDescription = null;
    }

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

    return new ChangeEvent<>(
        document.getDocument(Fields.ID_FIELD),
        OperationType.fromRemote(
            document.getString(Fields.OPERATION_TYPE_FIELD).getValue()),
        fullDocument,
        new MongoNamespace(
            nsDoc.getString(Fields.NS_DB_FIELD).getValue(),
            nsDoc.getString(Fields.NS_COLL_FIELD).getValue()),
        document.getDocument(Fields.DOCUMENT_KEY_FIELD),
        updateDescription,
        document.getBoolean(
            Fields.WRITE_PENDING_FIELD,
            BsonBoolean.FALSE).getValue());
  }

  // remove me
  private static final class Fields {
    static final String ID_FIELD = "_id";
    static final String OPERATION_TYPE_FIELD = "operationType";
    static final String FULL_DOCUMENT_FIELD = "fullDocument";
    static final String DOCUMENT_KEY_FIELD = "documentKey";

    static final String NS_FIELD = "ns";
    static final String NS_DB_FIELD = "db";
    static final String NS_COLL_FIELD = "coll";

    static final String UPDATE_DESCRIPTION_FIELD = "updateDescription";
    static final String UPDATE_DESCRIPTION_UPDATED_FIELDS_FIELD = "updatedFields";
    static final String UPDATE_DESCRIPTION_REMOVED_FIELDS_FIELD = "removedFields";

    static final String WRITE_PENDING_FIELD = "writePending";
  }
}
