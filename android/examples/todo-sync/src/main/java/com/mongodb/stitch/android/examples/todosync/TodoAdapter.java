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

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.google.android.gms.tasks.Task;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.types.ObjectId;

public class TodoAdapter extends RecyclerView.Adapter<TodoAdapter.TodoItemViewHolder> {
  private static final String TAG = TodoAdapter.class.getSimpleName();
  private final ItemUpdater itemUpdater;
  private List<TodoItem> todoItems;
  private Map<ObjectId, Integer> todoItemIdsToIdx;

  TodoAdapter(final List<TodoItem> todoItems, final ItemUpdater itemUpdater) {
    this.todoItems = todoItems;
    this.todoItemIdsToIdx = new HashMap<>();
    for (int i = 0; i < this.todoItems.size(); i++) {
      final TodoItem item = this.todoItems.get(i);
      this.todoItemIdsToIdx.put(item.getId(), i);
    }
    this.itemUpdater = itemUpdater;
  }

  // Create new views (invoked by the layout manager)
  @NonNull
  @Override
  public TodoItemViewHolder onCreateViewHolder(
      @NonNull final ViewGroup parent,
      final int viewType
  ) {
    final View v = LayoutInflater.from(parent.getContext())
        .inflate(R.layout.todo_item, parent, false);

    return new TodoItemViewHolder(v);
  }

  // Replace the contents of a view (invoked by the layout manager)
  @Override
  public void onBindViewHolder(@NonNull final TodoItemViewHolder holder, final int position) {
    final TodoItem item = todoItems.get(position);

    holder.taskTextView.setText(item.getTask());
    holder.taskCheckbox.setChecked(item.isChecked());
  }

  @Override
  public int getItemCount() {
    return todoItems.size();
  }

  public synchronized void updateOrAddItem(final TodoItem todoItem) {
    if (todoItemIdsToIdx.containsKey(todoItem.getId())) {
      todoItems.set(todoItemIdsToIdx.get(todoItem.getId()), todoItem);
    } else {
      todoItems.add(todoItem);
      todoItemIdsToIdx.put(todoItem.getId(), todoItems.size() - 1);
    }
    TodoAdapter.this.notifyDataSetChanged();
  }

  public synchronized void removeItemById(final ObjectId id) {
    if (!todoItemIdsToIdx.containsKey(id)) {
      return;
    }
    todoItems.remove((int) todoItemIdsToIdx.remove(id));
    // update indexes
    for (int i = 0; i < todoItems.size(); i++) {
      final TodoItem item = todoItems.get(i);
      todoItemIdsToIdx.put(item.getId(), i);
    }
    TodoAdapter.this.notifyDataSetChanged();
  }

  public synchronized void replaceItems(
      final Task<List<TodoItem>> newTodoItems, final boolean removeUnseen) {
    newTodoItems.addOnCompleteListener(task -> {
      if (!task.isSuccessful()) {
        Log.e(TAG, "failed to get items", task.getException());
        return;
      }
      final Set<Integer> unseenIdxs = new HashSet<>(todoItemIdsToIdx.values());
      final List<TodoItem> newItems = task.getResult();

      // replace existing items and add new ones
      for (final TodoItem newItem : newItems) {
        if (todoItemIdsToIdx.containsKey(newItem.getId())) {
          final Integer idx = todoItemIdsToIdx.get(newItem.getId());
          todoItems.set(idx, newItem);
          unseenIdxs.remove(idx);
        } else {
          todoItems.add(newItem);
        }
      }
      if (removeUnseen) {
        // remove all items we did not see in new list
        for (final Integer idx : unseenIdxs) {
          final TodoItem removedItem = todoItems.remove((int) idx);
          todoItemIdsToIdx.remove(removedItem.getId());
        }
      }
      // update indexes
      for (int i = 0; i < todoItems.size(); i++) {
        final TodoItem item = todoItems.get(i);
        todoItemIdsToIdx.put(item.getId(), i);
      }
      TodoAdapter.this.notifyDataSetChanged();
    });
  }

  public synchronized void clearItems() {
    todoItems.clear();
    todoItemIdsToIdx.clear();
  }

  // Callback for checkbox updates
  interface ItemUpdater {
    void updateChecked(ObjectId itemId, boolean isChecked);

    void updateTask(ObjectId itemId, String currentTask);
  }

  class TodoItemViewHolder extends RecyclerView.ViewHolder
      implements View.OnClickListener,
          View.OnLongClickListener,
          CompoundButton.OnCheckedChangeListener {
    final TextView taskTextView;
    final CheckBox taskCheckbox;

    TodoItemViewHolder(final View view) {
      super(view);
      taskTextView = view.findViewById(R.id.tv_task);
      taskCheckbox = view.findViewById(R.id.cb_todo_checkbox);

      // Set listeners
      taskCheckbox.setOnCheckedChangeListener(this);
      view.setOnClickListener(this);
      view.setOnLongClickListener(this);
      taskCheckbox.setOnClickListener(this);
      taskCheckbox.setOnLongClickListener(this);
    }

    @Override
    public synchronized void onCheckedChanged(
        final CompoundButton compoundButton,
        final boolean isChecked
    ) {
    }

    @Override
    public void onClick(final View view) {
      if (getAdapterPosition() == RecyclerView.NO_POSITION) {
        return;
      }
      final TodoItem item = todoItems.get(getAdapterPosition());
      itemUpdater.updateChecked(item.getId(), taskCheckbox.isChecked());
    }

    @Override
    public synchronized boolean onLongClick(final View view) {
      if (getAdapterPosition() == RecyclerView.NO_POSITION) {
        return false;
      }
      final TodoItem item = todoItems.get(getAdapterPosition());
      itemUpdater.updateTask(item.getId(), item.getTask());
      return true;
    }
  }
}
