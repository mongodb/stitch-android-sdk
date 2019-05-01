package com.mongodb.stitch.android.examples.chatsync.repo

import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.examples.chatsync.defaultRegistry
import com.mongodb.stitch.android.examples.chatsync.model.User
import com.mongodb.stitch.android.examples.chatsync.remoteClient
import com.mongodb.stitch.android.examples.chatsync.stitch
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncInsertOneResult
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncUpdateResult
import kotlinx.coroutines.withContext
import org.bson.BsonString
import org.bson.BsonValue
import org.bson.Document
import kotlin.coroutines.coroutineContext

object UserRepo : SyncRepo<User, String>(cacheSize = 30) {
    override val collection: RemoteMongoCollection<User> by lazy {
        remoteClient
            .getDatabase("chats")
            .getCollection("users", User::class.java)
            .withCodecRegistry(defaultRegistry)
    }

    override fun idToBson(id: String): BsonValue = BsonString(id)

    suspend fun findCurrentUser(): User? = withContext(coroutineContext) {
        stitch.auth.user?.let {
            readFromCache(it.id)
                ?: Tasks.await(collection.sync().find(Document("_id", it.id)).first())
        }
    }

    suspend fun insertCurrentUser(user: User): SyncInsertOneResult =
        withContext(coroutineContext) {
            Tasks.await(collection.sync().insertOne(user))
        }

    suspend fun updateAvatar(avatar: ByteArray): SyncUpdateResult = withContext(coroutineContext) {
        val currentUser = checkNotNull(findCurrentUser())
        Tasks.await(collection.sync().updateOne(
            Document(mapOf("_id" to currentUser.id)),
            Document(mapOf("\$set" to mapOf("avatar" to avatar)))
        ))
    }
}
