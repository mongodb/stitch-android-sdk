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

package com.mongodb.stitch.android.examples.todosync;

import java.util.Date;

import org.bson.BsonBoolean;
import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.BsonReader;
import org.bson.BsonString;
import org.bson.BsonWriter;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.types.ObjectId;

class TodoItem {

  public static final String TODO_LIST_DATABASE = "todo";
  public static final String TODO_LIST_COLLECTION = "items";

  private final ObjectId id;
  private final String ownerId;
  private final String task;
  private final boolean checked;
  private Date doneDate;

  /** Constructs a todo item from a MongoDB document. */
  TodoItem(
      final ObjectId id,
      final String ownerId,
      final String task,
      final boolean checked,
      final Date doneDate
  ) {
    this.id = id;
    this.ownerId = ownerId;
    this.task = task;
    this.checked = checked;
    this.doneDate = doneDate;
  }

  public ObjectId getId() {
    return id;
  }

  public String getOwnerId() {
    return ownerId;
  }

  public String getTask() {
    return task;
  }

  public Boolean isChecked() {
    return checked;
  }

  public Date getDoneDate() {
    return doneDate;
  }

  static BsonDocument toBsonDocument(final TodoItem item) {
    final BsonDocument asDoc = new BsonDocument();
    asDoc.put(Fields.ID, new BsonObjectId(item.getId()));
    asDoc.put(Fields.OWNER_ID, new BsonString(item.getOwnerId()));
    asDoc.put(Fields.TASK, new BsonString(item.getTask()));
    asDoc.put(Fields.CHECKED, new BsonBoolean(item.isChecked()));
    if (item.getDoneDate() != null) {
      asDoc.put(Fields.DONE_DATE, new BsonDateTime(item.getDoneDate().getTime()));
    }
    return asDoc;
  }

  static TodoItem fromBsonDocument(final BsonDocument doc) {
    final Date doneDate;
    if (doc.containsKey(Fields.DONE_DATE)) {
      doneDate = new Date(doc.getDateTime(Fields.DONE_DATE).getValue());
    } else {
      doneDate = null;
    }
    return new TodoItem(
        doc.getObjectId(Fields.ID).getValue(),
        doc.getString(Fields.OWNER_ID).getValue(),
        doc.getString(Fields.TASK).getValue(),
        doc.getBoolean(Fields.CHECKED).getValue(),
        doneDate
    );
  }

  static final class Fields {
    static final String ID = "_id";
    static final String OWNER_ID = "owner_id";
    static final String TASK = "task";
    static final String CHECKED = "checked";
    static final String DONE_DATE = "done_date";
  }

  public static final Codec<TodoItem> codec = new Codec<TodoItem>() {

    @Override
    public void encode(
        final BsonWriter writer, final TodoItem value, final EncoderContext encoderContext) {
      new BsonDocumentCodec().encode(writer, toBsonDocument(value), encoderContext);
    }

    @Override
    public Class<TodoItem> getEncoderClass() {
      return TodoItem.class;
    }

    @Override
    public TodoItem decode(
        final BsonReader reader, final DecoderContext decoderContext) {
      final BsonDocument document = (new BsonDocumentCodec()).decode(reader, decoderContext);
      return fromBsonDocument(document);
    }
  };
}
