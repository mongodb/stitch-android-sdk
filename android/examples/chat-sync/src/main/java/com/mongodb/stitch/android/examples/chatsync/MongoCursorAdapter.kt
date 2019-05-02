package com.mongodb.stitch.android.examples.chatsync

import android.support.annotation.MainThread
import android.support.annotation.WorkerThread
import android.support.v7.widget.RecyclerView
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCursor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

abstract class MongoCursorAdapter<VH : RecyclerView.ViewHolder, T : Comparable<T>> :
    RecyclerView.Adapter<VH>(), CoroutineScope {

    private lateinit var job: Job

    override val coroutineContext: CoroutineContext
        get() = job + Main

    private val cache = sortedSetOf<T>()
    private var cursorPosition: Int = -1
    private lateinit var cursor: RemoteMongoCursor<T>
    private var itemCount: Int = 0

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        job = Job()
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        job.cancel()
    }

    final override fun onBindViewHolder(viewHolder: VH, position: Int) = synchronized(viewHolder) {
        runBlocking {
            launch(IO) {
                onBindSynchronizedViewHolder(viewHolder, position)
            }.join()
        }
    }

    abstract suspend fun onBindSynchronizedViewHolder(viewHolder: VH, position: Int)

    final override fun getItemCount(): Int = this.itemCount

    fun setCursor(cursor: RemoteMongoCursor<T>, itemCount: Int) {
        this.cursor = cursor
        this.itemCount = itemCount
        launch(Main) { notifyDataSetChanged() }
    }

    @WorkerThread
    suspend fun getItem(position: Int): T? = withContext(coroutineContext) {
        moveToPosition(position)
        cache.elementAt(position)
    }

    @WorkerThread
    private suspend fun moveToPosition(position: Int) = withContext(coroutineContext) {
        synchronized(this) {
            while (cursorPosition < position && Tasks.await(cursor.hasNext())) {
                val next = Tasks.await(cursor.next())
                cache.add(next)
                cursorPosition++
            }
        }
    }

    @MainThread
    fun upsert(obj: T) {
        val index = cache.indexOf(obj)
        if (index != -1) {
            Log.d(this::class.java.simpleName, "Item updated: $obj with index: $index")
            cache.remove(obj)
            cache.add(obj)
            this.notifyItemChanged(index)
        } else {
            Log.d(this::class.java.simpleName, "New item upserted: $obj")
            cache.add(obj)
            cursorPosition++
            itemCount++
            this.notifyItemInserted(0)
        }
    }
}
