package com.mongodb.stitch.examples.example1;

import org.bson.BsonReader;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.BsonWriter;
import org.bson.Document;
import org.bson.codecs.CollectibleCodec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.EncoderContext;
import org.bson.types.ObjectId;

import java.util.Date;

public class TodoItem {
    private ObjectId _id;
    private String task;
    private Boolean checked;
    private Date doneDate;

    public TodoItem(final ObjectId id, final String task, final Boolean checked, final Date doneDate) {
        this._id = id;
        this.task = task;
        this.checked = checked;
        this.doneDate = doneDate;
    }

    public TodoItem withNewObjectId() {
        setId(new ObjectId());
        return this;
    }

    public Boolean getChecked() {
        return checked;
    }
    public void setChecked(Boolean checked) { this.checked = checked; }

    public String getTask() {
        return task;
    }
    public void setTask(String task) { this.task = task; }

    public ObjectId getId() {
        return _id;
    }
    public void setId(ObjectId _id) { this._id = _id; }

    public Date getDoneDate() { return doneDate; }
    public void setDoneDate(Date doneDate) { this.doneDate = doneDate; }

    @Override
    public String toString() {
        return  "Task: " + getTask() + "\n" +
                "Done: " + getChecked() + "\n" +
                ((getDoneDate() != null) ? ("Done Date: " + getDoneDate().toString() + "\n") : "") +
                "ID: " + getId().toHexString() + "\n";
    }

    public static class Codec implements CollectibleCodec<TodoItem> {

        @Override
        public TodoItem generateIdIfAbsentFromDocument(TodoItem document) {
            return documentHasId(document) ? document.withNewObjectId() : document;
        }

        @Override
        public boolean documentHasId(TodoItem document) {
            return document.getId() == null;
        }

        @Override
        public BsonValue getDocumentId(TodoItem document) {
            return new BsonString(document.getId().toHexString());
        }

        @Override
        public TodoItem decode(BsonReader reader, DecoderContext decoderContext) {
            Document document = (new DocumentCodec()).decode(reader, decoderContext);

            TodoItem item = new TodoItem(
                    document.getObjectId("_id"),
                    document.getString("task"),
                    document.getBoolean("checked"),
                    document.getDate("done_date")
            );
            return item;
        }

        @Override
        public void encode(BsonWriter writer, TodoItem value, EncoderContext encoderContext) {

            Document document = new Document();
            if(value.getId() != null) {
                document.put("_id", value.getId());
            }
            if(value.getTask() != null) {
                document.put("task", value.getTask());
            }
            if(value.getChecked() != null) {
                document.put("checked", value.getChecked());
            }
            if(value.getDoneDate() != null) {
                document.put("done_date", value.getDoneDate());
            }
            (new DocumentCodec()).encode(writer, document, encoderContext);
        }

        @Override
        public Class<TodoItem> getEncoderClass() {
            return TodoItem.class;
        }
    }

}

