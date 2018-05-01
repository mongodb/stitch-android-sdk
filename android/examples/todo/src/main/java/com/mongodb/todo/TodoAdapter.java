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

package com.mongodb.todo;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import java.util.List;
import org.bson.types.ObjectId;

public class TodoAdapter extends RecyclerView.Adapter<TodoAdapter.TodoItemViewHolder> {
  private final ItemUpdater itemUpdater;
  private List<TodoItem> todoItems;

  public TodoAdapter(List<TodoItem> todoItems, ItemUpdater itemUpdater) {
    this.todoItems = todoItems;
    this.itemUpdater = itemUpdater;
  }

  // Create new views (invoked by the layout manager)
  @Override
  public TodoItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.todo_item, parent, false);

    TodoItemViewHolder vh = new TodoItemViewHolder(v);
    return vh;
  }

  // Replace the contents of a view (invoked by the layout manager)
  @Override
  public void onBindViewHolder(TodoItemViewHolder holder, int position) {
    final TodoItem item = todoItems.get(position);

    holder.taskTextView.setText(item.getTask());
    holder.taskCheckbox.setChecked(item.getChecked());
  }

  @Override
  public int getItemCount() {
    return todoItems.size();
  }

  public void updateItems(List<TodoItem> todoItems) {
    this.todoItems = todoItems;
    this.notifyDataSetChanged();
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

    TodoItemViewHolder(View view) {
      super(view);
      taskTextView = view.findViewById(R.id.tv_task);
      taskCheckbox = view.findViewById(R.id.cb_todo_checkbox);

      // Set listeners
      taskCheckbox.setOnCheckedChangeListener(this);
      view.setOnClickListener(this);
      view.setOnLongClickListener(this);
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
      TodoItem item = todoItems.get(getAdapterPosition());
      itemUpdater.updateChecked(item.getId(), isChecked);
    }

    @Override
    public void onClick(View view) {
      taskCheckbox.setChecked(!taskCheckbox.isChecked());
    }

    @Override
    public boolean onLongClick(View view) {
      TodoItem item = todoItems.get(getAdapterPosition());
      itemUpdater.updateTask(item.getId(), item.getTask());
      return true;
    }
  }
}
