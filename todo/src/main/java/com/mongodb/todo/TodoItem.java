package com.mongodb.todo;

import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.Date;

public class TodoItem {

    public static final String TODO_LIST_DATABASE = "todo";
    public static final String TODO_LIST_COLLECTION = "items";

    public static final String ID_KEY = "_id";
    public static final String TASK_KEY = "task";
    public static final String CHECKED_KEY = "checked";
    public static final String DONE_DATE_KEY = "done_date";

   private ObjectId _id;
   private String task;
   private Boolean checked;
   private Date doneDate;

   public static boolean isTodoItem(Document todoItemDoc) {
       return todoItemDoc.containsKey(ID_KEY) &&
               todoItemDoc.containsKey(TASK_KEY) &&
               todoItemDoc.containsKey(CHECKED_KEY);
   }

    public TodoItem(Document todoItemDoc) {
        this._id = todoItemDoc.getObjectId(ID_KEY);
        this.task = todoItemDoc.getString(TASK_KEY);
        this.checked = todoItemDoc.getBoolean(CHECKED_KEY);
        if(todoItemDoc.containsKey(DONE_DATE_KEY)) {
            this.doneDate = todoItemDoc.getDate(DONE_DATE_KEY);
        }
    }

    public Boolean getChecked() {
        return checked;
    }

    public String getTask() {
        return task;
    }

    public ObjectId getId() {
        return _id;
    }

    public Date getDoneDate() { return doneDate; }
}
