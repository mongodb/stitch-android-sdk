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

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.mongodb.stitch.android.core.Stitch;
import com.mongodb.stitch.android.core.StitchAppClient;
import com.mongodb.stitch.android.services.mongodb.sync.SyncMongoClient;
import com.mongodb.stitch.android.services.mongodb.sync.SyncMongoCollection;
import com.mongodb.stitch.core.auth.providers.userpassword.UserPasswordCredential;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult;
import com.mongodb.stitch.core.services.mongodb.sync.ChangeEventListener;
import com.mongodb.stitch.core.services.mongodb.sync.DefaultSyncConflictResolvers;
import com.mongodb.stitch.core.services.mongodb.sync.DocumentSynchronizationConfig;
import com.mongodb.stitch.core.services.mongodb.sync.internal.ChangeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.BsonObjectId;
import org.bson.BsonRegularExpression;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.types.ObjectId;

public class TodoListActivity extends AppCompatActivity {
  private static final String TAG = TodoListActivity.class.getSimpleName();

  private final ListUpdateListener listUpdateListener = new ListUpdateListener();
  private final ItemUpdateListener itemUpdateListener = new ItemUpdateListener();
  private TodoAdapter todoAdapter;
  private SyncMongoCollection<BsonDocument> lists;
  private SyncMongoCollection<Document> items;

  private static final String TODO_LISTS_DATABASE = "todo";
  private static final String TODO_LISTS_COLLECTION = "lists";

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_todo_list);

    // Set up Stitch and local MongoDB mobile client
    final StitchAppClient client = Stitch.getDefaultAppClient();

    client.getAuth().loginWithCredential(
        new UserPasswordCredential("unique_user@domain.com", "password"))
        .addOnFailureListener(new OnFailureListener() {
          @Override
          public void onFailure(@NonNull final Exception e) {
            Log.e(TAG, "failed to log into Stitch", e);
          }
        });

    final SyncMongoClient mongoClient = client.getServiceClient(
        SyncMongoClient.Factory, "Like");
    items =
        mongoClient
            .getDatabase(TodoItem.TODO_LIST_DATABASE)
            .getCollection(TodoItem.TODO_LIST_COLLECTION);
    lists =
        mongoClient
            .getDatabase(TODO_LISTS_DATABASE)
            .getCollection(TODO_LISTS_COLLECTION, BsonDocument.class);

    // Set up recycler view for to-do items
    final RecyclerView todoRecyclerView = findViewById(R.id.rv_todo_items);
    final RecyclerView.LayoutManager todoLayoutManager = new LinearLayoutManager(this);
    todoRecyclerView.setLayoutManager(todoLayoutManager);

    // Set up adapter
    todoAdapter = new TodoAdapter(
        Collections.<TodoItem>emptyList(),
        new TodoAdapter.ItemUpdater() {
          @Override
          public void updateChecked(final ObjectId itemId, final boolean isChecked) {
            final Document updateDoc =
                new Document("$set", new Document(TodoItem.CHECKED_KEY, isChecked));

            if (isChecked) {
              updateDoc.append("$currentDate", new Document(TodoItem.DONE_DATE_KEY, true));
            } else {
              updateDoc.append("$unset", new Document(TodoItem.DONE_DATE_KEY, ""));
            }

            items.updateOneById(new BsonObjectId(itemId), updateDoc);
          }

          @Override
          public void updateTask(final ObjectId itemId, final String currentTask) {
            showEditItemDialog(itemId, currentTask);
          }
        });
    todoRecyclerView.setAdapter(todoAdapter);
    todoAdapter.updateItems(getItems());

    for (final DocumentSynchronizationConfig config : items.getSynchronizedDocuments()) {
      items.sync(
          config.getDocumentId(),
          DefaultSyncConflictResolvers.<Document>remoteWins(),
          new ChangeEventListener<Document>() {
            @Override
            public void onEvent(final BsonValue documentId, final ChangeEvent<Document> event) {
              if (!event.isLocalWritePending()) {
                touchList();
              }
            }
          });
    }

    if (lists.getSynchronizedDocuments().isEmpty()) {
      lists.insertOneAndSync(
          new BsonDocument("_id", new BsonString("mylist")),
          DefaultSyncConflictResolvers.<BsonDocument>remoteWins(),
          listUpdateListener);
    } else {
      lists.sync(
          new BsonString("mylist"),
          DefaultSyncConflictResolvers.<BsonDocument>remoteWins(),
          listUpdateListener);
    }
  }

  private class ListUpdateListener implements ChangeEventListener<BsonDocument> {
    @Override
    public void onEvent(final BsonValue documentId, final ChangeEvent<BsonDocument> event) {
      todoAdapter.updateItems(getItems());
    }
  }

  private class ItemUpdateListener implements ChangeEventListener<Document> {
    @Override
    public void onEvent(final BsonValue documentId, final ChangeEvent<Document> event) {
      if (!event.isLocalWritePending()) {
        lists.updateOneById(
            new BsonString("mylist"),
            new BsonDocument("$inc", new BsonDocument("i", new BsonInt64(1))));
      }
    }
  }

  @Override
  public boolean onCreateOptionsMenu(final Menu menu) {
    final MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.todo_menu, menu);

    final SearchView searchView =
        (SearchView) menu.findItem(R.id.search_items_action).getActionView();
    searchView.setOnQueryTextListener(
        new SearchView.OnQueryTextListener() {
          @Override
          public boolean onQueryTextSubmit(final String query) {
            return false;
          }

          @Override
          public boolean onQueryTextChange(final String query) {
            todoAdapter.updateItems(getItemsWithRegexFilter(query));
            return true;
          }
        });

    searchView.setOnCloseListener(
        new SearchView.OnCloseListener() {
          @Override
          public boolean onClose() {
            todoAdapter.updateItems(getItems());
            return false;
          }
        });

    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(final MenuItem item) {
    switch (item.getItemId()) {
      case R.id.add_todo_item_action:
        showAddItemDialog();
        return true;
      case R.id.clear_checked_action:
        clearCheckedItems();
        return true;
      case R.id.clear_all_action:
        clearAllItems();
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  private Task<List<TodoItem>> getItems() {
    return items.find().into(new ArrayList<Document>())
        .continueWith(new Continuation<ArrayList<Document>, List<TodoItem>>() {
          @Override
          public List<TodoItem> then(@NonNull final Task<ArrayList<Document>> task) {
            if (!task.isSuccessful()) {
              return Collections.emptyList();
            }
            final List<TodoItem> todoItems = new ArrayList<>();
            for (final Document doc : task.getResult()) {
              if (TodoItem.isTodoItem(doc)) {
                final TodoItem item = new TodoItem(doc);
                items.sync(
                    new BsonObjectId(item.getId()),
                    DefaultSyncConflictResolvers.<Document>remoteWins(),
                    itemUpdateListener);
                todoItems.add(item);
              }
            }
            return todoItems;
          }
        });
  }

  private Task<List<TodoItem>> getItemsWithRegexFilter(final String regex) {
    return items.find(
        new Document(
            TodoItem.TASK_KEY,
            new Document().append("$regex",
                new BsonRegularExpression(regex)).append("$options", "i")))
        .into(new ArrayList<Document>())
        .continueWith(new Continuation<ArrayList<Document>, List<TodoItem>>() {
          @Override
          public List<TodoItem> then(@NonNull final Task<ArrayList<Document>> task) {
            if (!task.isSuccessful()) {
              return Collections.emptyList();
            }
            final List<TodoItem> todoItems = new ArrayList<>();
            for (final Document doc : task.getResult()) {
              if (TodoItem.isTodoItem(doc)) {
                final TodoItem item = new TodoItem(doc);
                items.sync(
                    new BsonObjectId(item.getId()),
                    DefaultSyncConflictResolvers.<Document>remoteWins(),
                    itemUpdateListener);;
                todoItems.add(item);
              }
            }
            return todoItems;
          }
        });
  }

  private void showAddItemDialog() {
    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Add Item");

    final View view = getLayoutInflater().inflate(R.layout.edit_item_dialog, null);
    final EditText input = view.findViewById(R.id.et_todo_item_task);

    builder.setView(view);

    // Set up the buttons
    builder.setPositiveButton(
        "Add",
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(final DialogInterface dialog, final int which) {
            addTodoItem(input.getText().toString());
          }
        });
    builder.setNegativeButton(
        "Cancel",
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(final DialogInterface dialog, final int which) {
            dialog.cancel();
          }
        });

    builder.show();
  }

  private void showEditItemDialog(final ObjectId itemId, final String currentTask) {
    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Edit Item");

    final View view = getLayoutInflater().inflate(R.layout.edit_item_dialog, null);
    final EditText input = view.findViewById(R.id.et_todo_item_task);

    input.setText(currentTask);
    input.setSelection(input.getText().length());

    builder.setView(view);

    // Set up the buttons
    builder.setPositiveButton(
        "Update",
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(final DialogInterface dialog, final int which) {
            updateTodoItemTask(itemId, input.getText().toString());
          }
        });
    builder.setNegativeButton(
        "Cancel",
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(final DialogInterface dialog, final int which) {
            dialog.cancel();
          }
        });

    builder.show();
  }

  private void updateTodoItemTask(final ObjectId itemId, final String newTask) {
    items.updateOneById(
        new BsonObjectId(itemId),
        new Document("$set", new Document(TodoItem.TASK_KEY, newTask)));
    todoAdapter.updateItems(getItems());
  }

  private void addTodoItem(final String task) {
    final Document newItem =
        new Document().append(TodoItem.TASK_KEY, task).append(TodoItem.CHECKED_KEY, false);
    final BsonValue id =
        items.insertOneAndSync(
            newItem,
            DefaultSyncConflictResolvers.<Document>remoteWins(),
            itemUpdateListener).getInsertedId();
    todoAdapter.updateItems(getItems());
  }

  private void touchList() {
    lists.updateOneById(
        new BsonString("mylist"),
        new BsonDocument("$inc", new BsonDocument("i", new BsonInt64(1))));
  }

  private void clearCheckedItems() {
    final List<Task<RemoteDeleteResult>> tasks = new ArrayList<>();
    getItems().addOnSuccessListener(new OnSuccessListener<List<TodoItem>>() {
      @Override
      public void onSuccess(final List<TodoItem> todoItems) {
        for (final TodoItem item : todoItems) {
          if (item.getChecked()) {
            tasks.add(items.deleteOneById(new BsonObjectId(item.getId())));
          }
        }
        Tasks.whenAllComplete(tasks).addOnCompleteListener(new OnCompleteListener<List<Task<?>>>() {
          @Override
          public void onComplete(@NonNull final Task<List<Task<?>>> task) {
            todoAdapter.updateItems(getItems());
          }
        });
      }
    });
  }

  private void clearAllItems() {
    final List<Task<RemoteDeleteResult>> tasks = new ArrayList<>();
    getItems().addOnSuccessListener(new OnSuccessListener<List<TodoItem>>() {
      @Override
      public void onSuccess(final List<TodoItem> todoItems) {
        for (final TodoItem item : todoItems) {
          tasks.add(items.deleteOneById(new BsonObjectId(item.getId())));
        }
        Tasks.whenAllComplete(tasks).addOnCompleteListener(new OnCompleteListener<List<Task<?>>>() {
          @Override
          public void onComplete(@NonNull final Task<List<Task<?>>> task) {
            todoAdapter.updateItems(getItems());
          }
        });
      }
    });
  }
}
