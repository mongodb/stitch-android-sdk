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

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.internal.common.BsonUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonReader;
import org.bson.BsonString;
import org.bson.BsonType;
import org.bson.BsonValue;
import org.bson.BsonWriter;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.Codec;
import org.bson.codecs.Decoder;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;

// TODO: Should there be a local and remote type for the pending part?
public final class ChangeEvent<DocumentT> {
  private final BsonDocument id; // Metadata related to the operation (the resumeToken).
  private final OperationType operationType;
  private final DocumentT fullDocument;
  private final MongoNamespace ns;
  private final BsonDocument documentKey;
  private final UpdateDescription updateDescription;
  private final boolean localWritePending;

  ChangeEvent(
      final BsonDocument id,
      final OperationType operationType,
      final DocumentT fullDocument,
      final MongoNamespace ns,
      final BsonDocument documentKey,
      final UpdateDescription updateDescription,
      final boolean localWritePending
  ) {
    this.id = id;
    this.operationType = operationType;
    this.fullDocument = fullDocument;
    this.ns = ns;
    this.documentKey = documentKey;
    this.updateDescription = updateDescription == null
        ? new UpdateDescription(null, null) : updateDescription;
    this.localWritePending = localWritePending;
  }

  public BsonDocument getId() {
    return id;
  }

  public OperationType getOperationType() {
    return operationType;
  }

  public DocumentT getFullDocument() {
    return fullDocument;
  }

  public MongoNamespace getNamespace() {
    return ns;
  }

  public BsonDocument getDocumentKey() {
    return documentKey;
  }

  public UpdateDescription getUpdateDescription() {
    return updateDescription;
  }

  public boolean isLocalWritePending() {
    return localWritePending;
  }

  public enum OperationType {
    INSERT, DELETE, REPLACE, UPDATE, UNKNOWN;

    static OperationType fromRemote(final String type) {
      switch (type) {
        case "insert":
          return INSERT;
        case "delete":
          return DELETE;
        case "replace":
          return REPLACE;
        case "update":
          return UPDATE;
        default:
          return UNKNOWN;
      }
    }

    String toRemote() {
      switch (this) {
        case INSERT:
          return "insert";
        case DELETE:
          return "delete";
        case REPLACE:
          return "replace";
        case UPDATE:
          return "update";
        default:
          return "unknown";
      }
    }
  }

  public static final class UpdateDescription {
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
  }

  static BsonDocument toBsonDocument(final ChangeEvent<BsonDocument> value) {
    final BsonDocument asDoc = new BsonDocument();
    asDoc.put(ChangeEventCoder.Fields.ID_FIELD, value.getId());
    asDoc.put(ChangeEventCoder.Fields.OPERATION_TYPE_FIELD,
        new BsonString(value.getOperationType().toRemote()));
    final BsonDocument nsDoc = new BsonDocument();
    nsDoc.put(ChangeEventCoder.Fields.NS_DB_FIELD,
        new BsonString(value.getNamespace().getDatabaseName()));
    nsDoc.put(ChangeEventCoder.Fields.NS_COLL_FIELD,
        new BsonString(value.getNamespace().getCollectionName()));
    asDoc.put(ChangeEventCoder.Fields.NS_FIELD, nsDoc);
    asDoc.put(ChangeEventCoder.Fields.DOCUMENT_KEY_FIELD, value.getDocumentKey());
    if (value.getFullDocument() != null) {
      asDoc.put(ChangeEventCoder.Fields.FULL_DOCUMENT_FIELD, value.getFullDocument());
    }
    if (value.getUpdateDescription() != null) {
      final BsonDocument updateDescDoc = new BsonDocument();
      updateDescDoc.put(
          ChangeEventCoder.Fields.UPDATE_DESCRIPTION_UPDATED_FIELDS_FIELD,
          value.getUpdateDescription().getUpdatedFields());

      final BsonArray removedFields = new BsonArray();
      for (final String field : value.getUpdateDescription().getRemovedFields()) {
        removedFields.add(new BsonString(field));
      }
      updateDescDoc.put(
          ChangeEventCoder.Fields.UPDATE_DESCRIPTION_REMOVED_FIELDS_FIELD,
          removedFields);
      asDoc.put(ChangeEventCoder.Fields.UPDATE_DESCRIPTION_FIELD, updateDescDoc);
    }
    return asDoc;
  }

  static ChangeEvent<BsonDocument> fromBsonDocument(final BsonDocument document) {
    keyPresent(ChangeEventCoder.Fields.ID_FIELD, document);
    keyPresent(ChangeEventCoder.Fields.OPERATION_TYPE_FIELD, document);
    keyPresent(ChangeEventCoder.Fields.NS_FIELD, document);
    keyPresent(ChangeEventCoder.Fields.DOCUMENT_KEY_FIELD, document);

    final BsonDocument nsDoc = document.getDocument(ChangeEventCoder.Fields.NS_FIELD);
    final ChangeEvent.UpdateDescription updateDescription;
    if (document.containsKey(ChangeEventCoder.Fields.UPDATE_DESCRIPTION_FIELD)) {
      final BsonDocument updateDescDoc =
          document.getDocument(ChangeEventCoder.Fields.UPDATE_DESCRIPTION_FIELD);
      keyPresent(ChangeEventCoder.Fields.UPDATE_DESCRIPTION_UPDATED_FIELDS_FIELD, updateDescDoc);
      keyPresent(ChangeEventCoder.Fields.UPDATE_DESCRIPTION_REMOVED_FIELDS_FIELD, updateDescDoc);

      final BsonArray removedFieldsArr =
          updateDescDoc.getArray(ChangeEventCoder.Fields.UPDATE_DESCRIPTION_REMOVED_FIELDS_FIELD);
      final Collection<String> removedFields = new ArrayList<>(removedFieldsArr.size());
      for (final BsonValue field : removedFieldsArr) {
        removedFields.add(field.asString().getValue());
      }
      updateDescription = new ChangeEvent.UpdateDescription(updateDescDoc.getDocument(
          ChangeEventCoder.Fields.UPDATE_DESCRIPTION_UPDATED_FIELDS_FIELD),
          removedFields);
    } else {
      updateDescription = null;
    }

    final BsonDocument fullDocument;
    if (document.containsKey(ChangeEventCoder.Fields.FULL_DOCUMENT_FIELD)) {
      final BsonValue fdVal = document.get(ChangeEventCoder.Fields.FULL_DOCUMENT_FIELD);
      if (fdVal.isDocument()) {
        fullDocument = fdVal.asDocument();
      } else {
        fullDocument = null;
      }
    } else {
      fullDocument = null;
    }

    return new ChangeEvent<>(
        document.getDocument(ChangeEventCoder.Fields.ID_FIELD),
        ChangeEvent.OperationType.fromRemote(
            document.getString(ChangeEventCoder.Fields.OPERATION_TYPE_FIELD).getValue()),
        fullDocument,
        new MongoNamespace(
            nsDoc.getString(ChangeEventCoder.Fields.NS_DB_FIELD).getValue(),
            nsDoc.getString(ChangeEventCoder.Fields.NS_COLL_FIELD).getValue()),
        document.getDocument(ChangeEventCoder.Fields.DOCUMENT_KEY_FIELD),
        updateDescription,
        nsDoc.getBoolean(
            ChangeEventCoder.Fields.WRITE_PENDING_FIELD,
            BsonBoolean.FALSE).getValue());
  }

  static final ChangeEventsDecoder changeEventsDecoder = new ChangeEventsDecoder();

  private static class ChangeEventsDecoder
      implements Decoder<List<Map.Entry<BsonValue, ChangeEvent<BsonDocument>>>> {
    public List<Map.Entry<BsonValue, ChangeEvent<BsonDocument>>> decode(
        final BsonReader reader,
        final DecoderContext decoderContext
    ) {
      final LinkedHashMap<BsonValue, ChangeEvent<BsonDocument>> latestEvents =
          new LinkedHashMap<>();
      reader.readStartArray();
      while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
        final ChangeEvent<BsonDocument> event = changeEventCoder.decode(reader, decoderContext);
        final BsonValue docId = event.getDocumentKey().get("_id");
        if (latestEvents.containsKey(docId)) {
          latestEvents.remove(docId);
        }
        latestEvents.put(docId, event);
      }
      reader.readEndArray();
      return new ArrayList<>(latestEvents.entrySet());
    }
  }

  static final ChangeEventCoder changeEventCoder = new ChangeEventCoder();

  static class ChangeEventCoder implements Codec<ChangeEvent<BsonDocument>> {
    public ChangeEvent<BsonDocument> decode(
        final BsonReader reader,
        final DecoderContext decoderContext
    ) {
      final BsonDocument document = (new BsonDocumentCodec()).decode(reader, decoderContext);
      return fromBsonDocument(document);
    }

    @Override
    public void encode(
        final BsonWriter writer,
        final ChangeEvent<BsonDocument> value,
        final EncoderContext encoderContext
    ) {
      new BsonDocumentCodec().encode(writer, toBsonDocument(value), encoderContext);
    }

    @Override
    public Class<ChangeEvent<BsonDocument>> getEncoderClass() {
      return null;
    }

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

      static final String WRITE_PENDING_FIELD = "write_pending";
    }
  }

  /**
   * Generates a change event for a local insert of the given document in the given namespace.
   *
   * @param namespace the namespace where the document was inserted.
   * @param document the document that was inserted.
   * @return a change event for a local insert of the given document in the given namespace.
   */
  static ChangeEvent<BsonDocument> changeEventForLocalInsert(
      final MongoNamespace namespace,
      final BsonDocument document,
      final boolean writePending
  ) {
    final BsonValue docId = BsonUtils.getDocumentId(document);
    return new ChangeEvent<>(
        new BsonDocument(),
        ChangeEvent.OperationType.INSERT,
        document,
        namespace,
        new BsonDocument("_id", docId),
        null,
        writePending);
  }

  /**
   * Generates a change event for a local update of a document in the given namespace referring
   * to the given document _id.
   *
   * @param namespace the namespace where the document was inserted.
   * @param documentId the _id of the document that was updated.
   * @param update the update specifier.
   * @return a change event for a local update of a document in the given namespace referring
   *         to the given document _id.
   */
  static ChangeEvent<BsonDocument> changeEventForLocalUpdate(
      final MongoNamespace namespace,
      final BsonValue documentId,

      // Unused for now since embedded does not offer change streams
      // nor a way to get an UpdateDescription. This results in updates
      // being processed as full document replacements (FDRs). Some
      // workarounds are diffing the previous and new document or parsing
      // the update modifiers against the old document. FDRs are bad
      // because they require more permissive Stitch rules.
      final BsonDocument update,
      final BsonDocument document,
      final boolean writePending
  ) {
    return new ChangeEvent<>(
        new BsonDocument(),
        ChangeEvent.OperationType.UPDATE,
        document,
        namespace,
        new BsonDocument("_id", documentId),
        null,
        writePending);
  }

  /**
   * Generates a change event for a local replacement of a document in the given namespace referring
   * to the given document _id.
   *
   * @param namespace the namespace where the document was inserted.
   * @param documentId the _id of the document that was updated.
   * @param document the replacement document.
   * @return a change event for a local replacement of a document in the given namespace referring
   *         to the given document _id.
   */
  static ChangeEvent<BsonDocument> changeEventForLocalReplace(
      final MongoNamespace namespace,
      final BsonValue documentId,
      final BsonDocument document,
      final boolean writePending
  ) {
    return new ChangeEvent<>(
        new BsonDocument(),
        ChangeEvent.OperationType.REPLACE,
        document,
        namespace,
        new BsonDocument("_id", documentId),
        null,
        writePending);
  }

  /**
   * Generates a change event for a local deletion of a document in the given namespace referring
   * to the given document _id.
   *
   * @param namespace the namespace where the document was inserted.
   * @param documentId the _id of the document that was updated.
   * @return a change event for a local deletion of a document in the given namespace referring
   *         to the given document _id.
   */
  static ChangeEvent<BsonDocument> changeEventForLocalDelete(
      final MongoNamespace namespace,
      final BsonValue documentId,
      final boolean writePending
  ) {
    return new ChangeEvent<>(
        new BsonDocument(),
        ChangeEvent.OperationType.DELETE,
        null,
        namespace,
        new BsonDocument("_id", documentId),
        null,
        writePending);
  }

  /**
   * Transforms a {@link ChangeEvent} into one that can be used by a user defined conflict resolver.
   * @param event the event to transform.
   * @param codec the codec to use to transform any documents specific to the collection.
   * @return the transformed {@link ChangeEvent}
   */
  static ChangeEvent transformChangeEventForUser(
      final ChangeEvent<BsonDocument> event,
      final Codec codec
  ) {
    return new ChangeEvent<>(
        event.getId(),
        event.getOperationType(),
        event.getFullDocument() == null ? null : codec
            .decode(event.getFullDocument().asBsonReader(), DecoderContext.builder().build()),
        event.getNamespace(),
        event.getDocumentKey(),
        event.getUpdateDescription(),
        event.isLocalWritePending());
  }
}
