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

package com.mongodb.stitch.android.examples.todo;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.stitch.android.core.Stitch;
import com.mongodb.stitch.android.core.StitchAppClient;
import com.mongodb.stitch.android.services.mongodb.local.LocalMongoDbService;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;

public class TodoListActivity extends AppCompatActivity {
  private TodoAdapter todoAdapter;
  private MongoCollection<Document> items;

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_todo_list);

    // Set up Stitch and local MongoDB mobile client
    final StitchAppClient client = Stitch.getDefaultAppClient();
    final MongoClient mongoClient = client.getServiceClient(LocalMongoDbService.clientFactory);
    items =
        mongoClient
            .getDatabase(TodoItem.TODO_LIST_DATABASE)
            .getCollection(TodoItem.TODO_LIST_COLLECTION);

    // Set up recycler view for to-do items
    final RecyclerView todoRecyclerView = findViewById(R.id.rv_todo_items);
    final RecyclerView.LayoutManager todoLayoutManager = new LinearLayoutManager(this);
    todoRecyclerView.setLayoutManager(todoLayoutManager);

    // Set up adapter
    todoAdapter =
        new TodoAdapter(
            getItems(),
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

              items.updateOne(new Document(TodoItem.ID_KEY, itemId), updateDoc);
            }

            @Override
            public void updateTask(final ObjectId itemId, final String currentTask) {
              showEditItemDialog(itemId, currentTask);
            }
          });
    todoRecyclerView.setAdapter(todoAdapter);
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

    return true;
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

  private List<TodoItem> getItems() {
    final ArrayList<TodoItem> todoItems = new ArrayList<>();
    for (final Document doc : items.find()) {
      if (TodoItem.isTodoItem(doc)) {
        final TodoItem item = new TodoItem(doc);
        todoItems.add(item);
      }
    }
    return todoItems;
  }

  private List<TodoItem> getItemsWithRegexFilter(final String regex) {
    final ArrayList<TodoItem> todoItems = new ArrayList<>();
    for (final Document doc :
        items.find(
            new Document(
                TodoItem.TASK_KEY,
                new Document().append("$regex", regex).append("$options", "i")))) {
      if (TodoItem.isTodoItem(doc)) {
        final TodoItem item = new TodoItem(doc);
        todoItems.add(item);
      }
    }
    return todoItems;
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
    items.updateOne(
        new Document(TodoItem.ID_KEY, itemId),
        new Document("$set", new Document(TodoItem.TASK_KEY, newTask)));
    todoAdapter.updateItems(getItems());
  }

  private void addTodoItem(final String task) {
    final Document newItem =
        new Document().append(TodoItem.TASK_KEY, task).append(TodoItem.CHECKED_KEY, false);
    items.insertOne(newItem);
    todoAdapter.updateItems(getItems());
  }

  private void clearCheckedItems() {
    items.deleteMany(new Document(TodoItem.CHECKED_KEY, true));
    todoAdapter.updateItems(getItems());
  }

  private void clearAllItems() {
    items.drop();
    todoAdapter.updateItems(getItems());
  }
}
