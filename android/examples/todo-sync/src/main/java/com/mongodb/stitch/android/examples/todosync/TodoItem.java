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
import org.bson.Document;
import org.bson.types.ObjectId;

class TodoItem {

  public static final String TODO_LIST_DATABASE = "todo";
  public static final String TODO_LIST_COLLECTION = "items";

  public static final String ID_KEY = "_id";
  public static final String TASK_KEY = "task";
  public static final String CHECKED_KEY = "checked";
  public static final String DONE_DATE_KEY = "done_date";

  private final ObjectId id;
  private final String task;
  private final Boolean checked;
  private Date doneDate;

  /** Constructs a todo item from a MongoDB document. */
  TodoItem(final Document todoItemDoc) {
    this.id = todoItemDoc.getObjectId(ID_KEY);
    this.task = todoItemDoc.getString(TASK_KEY);
    this.checked = todoItemDoc.getBoolean(CHECKED_KEY);
    if (todoItemDoc.containsKey(DONE_DATE_KEY)) {
      this.doneDate = todoItemDoc.getDate(DONE_DATE_KEY);
    }
  }

  /** Returns if a MongoDB document is a todo item. */
  public static boolean isTodoItem(final Document todoItemDoc) {
    return todoItemDoc.containsKey(ID_KEY)
        && todoItemDoc.containsKey(TASK_KEY)
        && todoItemDoc.containsKey(CHECKED_KEY);
  }

  public Boolean getChecked() {
    return checked;
  }

  public String getTask() {
    return task;
  }

  public ObjectId getId() {
    return id;
  }

  public Date getDoneDate() {
    return new Date(doneDate.getTime());
  }
}
