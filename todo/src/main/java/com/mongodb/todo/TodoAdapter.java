package com.mongodb.todo;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import org.bson.types.ObjectId;

import java.util.List;

public class TodoAdapter extends RecyclerView.Adapter<TodoAdapter.TodoItemViewHolder> {
    // Callback for checkbox updates
    interface ItemUpdater {
        void updateChecked(ObjectId itemId, boolean isChecked);
        void updateTask(ObjectId itemId, String currentTask);
    }

    private final ItemUpdater mItemUpdater;
    private List<TodoItem> mTodoItems;


    public TodoAdapter(List<TodoItem> todoItems, ItemUpdater itemUpdater) {
        mTodoItems = todoItems;
        mItemUpdater = itemUpdater;
    }

    // Create new views (invoked by the layout manager)
    @Override
    public TodoItemViewHolder onCreateViewHolder(ViewGroup parent,
                                                   int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.todo_item, parent, false);

        TodoItemViewHolder vh = new TodoItemViewHolder(v);
        return vh;
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(TodoItemViewHolder holder, int position) {
        final TodoItem item = mTodoItems.get(position);

        holder.taskTextView.setText(item.getTask());
        holder.taskCheckbox.setChecked(item.getChecked());
    }

    @Override
    public int getItemCount() {
        return mTodoItems.size();
    }

    public void updateItems(List<TodoItem> todoItems) {
        mTodoItems = todoItems;
        this.notifyDataSetChanged();
    }

    class TodoItemViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener, CompoundButton.OnCheckedChangeListener {
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
            TodoItem item = mTodoItems.get(getAdapterPosition());
            mItemUpdater.updateChecked(item.getId(), isChecked);
        }

        @Override
        public void onClick(View view) {
            taskCheckbox.setChecked(!taskCheckbox.isChecked());
        }

        @Override
        public boolean onLongClick(View view) {
            TodoItem item = mTodoItems.get(getAdapterPosition());
            mItemUpdater.updateTask(item.getId(), item.getTask());
            return true;
        }
    }
}
