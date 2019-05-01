package com.mongodb.stitch.android.examples.chatsync.repo

import android.arch.lifecycle.LiveData
import android.support.annotation.MainThread
import android.support.v4.util.LruCache
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection
import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.SyncConfiguration
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bson.BsonValue
import org.bson.Document
import kotlin.coroutines.coroutineContext

@Suppress("unused")
class ReadOnlyLruCache<K, V> constructor(private val lruCache: LruCache<K, V>) {
    operator fun get(k: K): V? = lruCache[k]
    fun size(): Int = lruCache.size()
    fun maxSize(): Int = lruCache.maxSize()
    fun hitCount(): Int = lruCache.hitCount()
    fun missCount(): Int = lruCache.missCount()
    fun createCount(): Int = lruCache.createCount()
    fun putCount(): Int = lruCache.putCount()
    fun evictionCount(): Int = lruCache.evictionCount()
}

abstract class SyncRepo<DocumentT, IdType>(private val cacheSize: Int) {
    protected abstract val collection: RemoteMongoCollection<DocumentT>

    private val cache by lazy { LruCache<Int, DocumentT>(cacheSize) }
    inner class LiveCache : LiveData<ReadOnlyLruCache<Int, DocumentT>>() {
        @MainThread
        fun updateCache(cache: ReadOnlyLruCache<Int, DocumentT>) {
            GlobalScope.launch(Main) {
                value = cache
            }
        }
    }
    val liveCache by lazy { LiveCache() }

    abstract fun idToBson(id: IdType): BsonValue

    suspend fun sync(vararg ids: IdType): Void? = withContext(coroutineContext) {
        Tasks.await(collection.sync().syncMany(*ids.map(::idToBson).toTypedArray()))
    }

    suspend fun <T, V> configure(
        conflictHandler: T,
        listener: V,
        exceptionListener: ExceptionListener? = null
    ): Void? where T : ConflictHandler<DocumentT>, V : ChangeEventListener<DocumentT> =
        withContext(coroutineContext) {
            Tasks.await(collection.sync().configure(
                SyncConfiguration.Builder()
                    .withConflictHandler(conflictHandler)
                    .withChangeEventListener(listener)
                    .withExceptionListener(exceptionListener)
                    .build()))
        }

    suspend fun findLocalById(id: IdType): DocumentT? = withContext(coroutineContext) {
        cache[id.hashCode()] ?: Tasks.await(
            collection.sync().find(Document(mapOf("_id" to id))).first()
        )?.let {
            Log.w("SyncRepo", "Adding $id to liveCache!")
            putIntoCache(id, it)
        }
    }


    fun refreshCacheForId(id: IdType) {
        if (cache[id.hashCode()] == null) {
            GlobalScope.launch(IO) {
                Tasks.await(
                    collection.sync().find(Document(mapOf("_id" to id))).first()
                )?.let {
                    putIntoCache(id, it)
                }
            }
        }
    }

    fun readFromCache(id: IdType): DocumentT? = cache[id.hashCode()]

    fun putIntoCache(id: IdType, document: DocumentT): DocumentT? {
        return cache.put(id.hashCode(), document).also {
            liveCache.updateCache(ReadOnlyLruCache(cache))
        }
    }
}
