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

import android.annotation.SuppressLint;
import android.os.Bundle;
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
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.mongodb.stitch.android.core.Stitch;
import com.mongodb.stitch.android.core.StitchAppClient;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection;
import com.mongodb.stitch.core.auth.providers.serverapikey.ServerApiKeyCredential;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener;
import com.mongodb.stitch.core.services.mongodb.remote.sync.DefaultSyncConflictResolvers;
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.ChangeEvent;

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
  private RemoteMongoCollection<BsonDocument> lists;
  private RemoteMongoCollection<Document> items;

  private static final String TODO_LISTS_DATABASE = "todo";
  private static final String TODO_LISTS_COLLECTION = "lists";

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_todo_list);

    // Set up Stitch and local MongoDB mobile client
    final StitchAppClient client = Stitch.getDefaultAppClient();

    final RemoteMongoClient mongoClient = client.getServiceClient(
        RemoteMongoClient.factory, "mongodb-atlas");
    items = mongoClient
        .getDatabase(TodoItem.TODO_LIST_DATABASE)
        .getCollection(TodoItem.TODO_LIST_COLLECTION);
    lists =
        mongoClient
            .getDatabase(TODO_LISTS_DATABASE)
            .getCollection(TODO_LISTS_COLLECTION, BsonDocument.class);

    client.getAuth().loginWithCredential(
        new ServerApiKeyCredential(
            "xEfxAP4jFWaWEs5WWpff7XyQMh1T56CCMmDEV9oxXtItPHBveA6bc6IEjOhQLes6"))
        .addOnSuccessListener(user -> {
          todoAdapter.updateItems(getItemsFromServer());

          if (lists.sync().getSyncedIds().isEmpty()) {
            lists.sync().insertOneAndSync(
                new BsonDocument("_id", new BsonString(user.getId())));
          }
        })
        .addOnFailureListener(e -> Log.e(TAG, "failed to log into Stitch", e));

    items.sync().configure(
        DefaultSyncConflictResolvers.remoteWins(),
        itemUpdateListener,
        (documentId, error) -> Log.e(TAG, error.getLocalizedMessage()));

    lists.sync().configure(
        DefaultSyncConflictResolvers.remoteWins(),
        listUpdateListener,
        (documentId, error) -> Log.e(TAG, error.getLocalizedMessage()));

    // Set up recycler view for to-do items
    final RecyclerView todoRecyclerView = findViewById(R.id.rv_todo_items);
    final RecyclerView.LayoutManager todoLayoutManager = new LinearLayoutManager(this);
    todoRecyclerView.setLayoutManager(todoLayoutManager);

    // Set up adapter
    todoAdapter = new TodoAdapter(
        Collections.emptyList(),
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

            items.sync().updateOneById(new BsonObjectId(itemId), updateDoc);
          }

          @Override
          public void updateTask(final ObjectId itemId, final String currentTask) {
            showEditItemDialog(itemId, currentTask);
          }
        });
    todoRecyclerView.setAdapter(todoAdapter);
  }

  private class ListUpdateListener implements ChangeEventListener<BsonDocument> {
    @Override
    public void onEvent(final BsonValue documentId, final ChangeEvent<BsonDocument> event) {
      if (!event.hasUncommittedWrites()) {
        todoAdapter.updateItems(getItemsFromServer());
      }
    }
  }

  private class ItemUpdateListener implements ChangeEventListener<Document> {
    @Override
    public void onEvent(final BsonValue documentId, final ChangeEvent<Document> event) {
      if (!event.hasUncommittedWrites()) {
        todoAdapter.updateItems(getItems());
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
        () -> {
          todoAdapter.updateItems(getItems());
          return false;
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

  private Task<List<TodoItem>> getItemsFromServer() {
    return items.find().into(new ArrayList<>())
        .continueWith(task -> {
          if (!task.isSuccessful()) {
            return Collections.emptyList();
          }
          final List<TodoItem> todoItems = new ArrayList<>();
          for (final Document doc : task.getResult()) {
            if (TodoItem.isTodoItem(doc)) {
              final TodoItem item = new TodoItem(doc);
              items.sync().syncOne(new BsonObjectId(item.getId()));
              todoItems.add(item);
            }
          }
          return todoItems;
        });
  }

  private Task<List<TodoItem>> getItems() {
    return items.sync().find().into(new ArrayList<>())
        .continueWith(task -> {
          if (!task.isSuccessful()) {
            return Collections.emptyList();
          }
          final List<TodoItem> todoItems = new ArrayList<>();
          for (final Document doc : task.getResult()) {
            if (TodoItem.isTodoItem(doc)) {
              final TodoItem item = new TodoItem(doc);
              todoItems.add(item);
            }
          }
          return todoItems;
        });
  }

  private Task<List<TodoItem>> getItemsWithRegexFilter(final String regex) {
    return items.sync().find(
        new Document(
            TodoItem.TASK_KEY,
            new Document().append("$regex",
                new BsonRegularExpression(regex)).append("$options", "i")))
        .into(new ArrayList<>())
        .continueWith(task -> {
          if (!task.isSuccessful()) {
            return Collections.emptyList();
          }
          final List<TodoItem> todoItems = new ArrayList<>();
          for (final Document doc : task.getResult()) {
            if (TodoItem.isTodoItem(doc)) {
              final TodoItem item = new TodoItem(doc);
              todoItems.add(item);
            }
          }
          return todoItems;
        });
  }

  private void showAddItemDialog() {
    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Add Item");

    @SuppressLint("InflateParams")
    final View view = getLayoutInflater().inflate(R.layout.edit_item_dialog, null);
    final EditText input = view.findViewById(R.id.et_todo_item_task);

    builder.setView(view);

    // Set up the buttons
    builder.setPositiveButton(
        "Add",
        (dialog, which) -> addTodoItem(input.getText().toString()));
    builder.setNegativeButton(
        "Cancel",
        (dialog, which) -> dialog.cancel());

    builder.show();
  }

  private void showEditItemDialog(final ObjectId itemId, final String currentTask) {
    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Edit Item");

    @SuppressLint("InflateParams")
    final View view = getLayoutInflater().inflate(R.layout.edit_item_dialog, null);
    final EditText input = view.findViewById(R.id.et_todo_item_task);

    input.setText(currentTask);
    input.setSelection(input.getText().length());

    builder.setView(view);

    // Set up the buttons
    builder.setPositiveButton(
        "Update",
        (dialog, which) -> updateTodoItemTask(itemId, input.getText().toString()));
    builder.setNegativeButton(
        "Cancel",
        (dialog, which) -> dialog.cancel());

    builder.show();
  }

  private void updateTodoItemTask(final ObjectId itemId, final String newTask) {
    items.sync().updateOneById(
        new BsonObjectId(itemId),
        new Document("$set", new Document(TodoItem.TASK_KEY, newTask)))
        .addOnSuccessListener(result -> todoAdapter.updateItems(getItems()))
        .addOnFailureListener(e -> Log.e(TAG, "failed to insert log item", e));
  }

  private void addTodoItem(final String task) {
    final Document newItem =
        new Document()
            .append(TodoItem.OWNER_ID, Stitch.getDefaultAppClient().getAuth().getUser().getId())
            .append(TodoItem.TASK_KEY, task)
            .append(TodoItem.CHECKED_KEY, false);
    items.sync().insertOneAndSync(newItem)
        .addOnSuccessListener(result -> {
          todoAdapter.updateItems(getItems());
          touchList();
        })
        .addOnFailureListener(e -> Log.e(TAG, "failed to insert log item", e));
  }

  private void touchList() {
    lists.sync().updateOneById(
        new BsonString(Stitch.getDefaultAppClient().getAuth().getUser().getId()),
        new BsonDocument("$inc", new BsonDocument("i", new BsonInt64(1))));
  }

  private void clearCheckedItems() {
    final List<Task<RemoteDeleteResult>> tasks = new ArrayList<>();
    getItems().addOnSuccessListener(todoItems -> {
      for (final TodoItem item : todoItems) {
        if (item.getChecked()) {
          tasks.add(items.sync().deleteOneById(new BsonObjectId(item.getId())));
        }
      }
      Tasks.whenAllComplete(tasks)
          .addOnCompleteListener(task -> todoAdapter.updateItems(getItems()));
    });
  }

  private void clearAllItems() {
    final List<Task<RemoteDeleteResult>> tasks = new ArrayList<>();
    getItems().addOnSuccessListener(todoItems -> {
      for (final TodoItem item : todoItems) {
        tasks.add(items.sync().deleteOneById(new BsonObjectId(item.getId())));
      }
      Tasks.whenAllComplete(tasks)
          .addOnCompleteListener(task -> todoAdapter.updateItems(getItems()));
    });
  }
}
