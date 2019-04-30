package com.mongodb.stitch.android.examples.chatsync

import android.support.v7.widget.RecyclerView
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCursor
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

abstract class MongoCursorAdapter<VH : RecyclerView.ViewHolder, T> : RecyclerView.Adapter<VH>() {
    private var cursor: SparseRemoteMongoCursor<T>? = null

    final override fun onBindViewHolder(viewHolder: VH, position: Int) {
        GlobalScope.launch(IO) {
            val cursor = checkNotNull(cursor)
            onBindViewHolder(viewHolder, position, cursor)
        }
    }

    abstract suspend fun onBindViewHolder(viewHolder: VH,
                                          position: Int,
                                          cursor: SparseRemoteMongoCursor<T>)

    fun setCursor(cursor: SparseRemoteMongoCursor<T>) {
        this.cursor = cursor
        GlobalScope.launch(Main) {
            notifyDataSetChanged()
        }
    }

    final override fun getItemCount(): Int = this.cursor?.count ?: 0

    fun put(obj: T) {
        val index = cursor?.put(obj) ?: -1
        Log.w("MongoCursorAdapter", "Putting obj in cursor: $obj with index: $index")
        if (index == -1) {
            this.notifyItemInserted(itemCount - 1)
        } else {
            this.notifyItemChanged(index)
        }
    }
}

class SparseRemoteMongoCursor<T>(private val cursor: RemoteMongoCursor<T>,
                                 var count: Int) {
    private val sparseCache = LinkedHashMap<Int, T>()
    private var cursorPosition: Int = -1

    suspend fun moveToPosition(position: Int) = withContext(coroutineContext) {
        synchronized(this@SparseRemoteMongoCursor) {
            while (cursorPosition < position && Tasks.await(cursor.hasNext())) {
                val next = Tasks.await(cursor.next())
                sparseCache[next.hashCode()] = next
                cursorPosition++
            }
        }
    }

    operator fun get(position: Int): T? = sparseCache[sparseCache.keys.elementAt(position)]

    fun put(obj: T): Int {
        val idx = sparseCache.keys.indexOf(obj.hashCode())
        if (idx != -1) {
            sparseCache[obj.hashCode()] = obj
            return idx
        }

        sparseCache[obj.hashCode()] = obj
        cursorPosition++
        count++
        return -1
    }
}
