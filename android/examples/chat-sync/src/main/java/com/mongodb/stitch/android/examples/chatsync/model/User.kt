package com.mongodb.stitch.android.examples.chatsync.model

import android.os.Parcelable
import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.examples.chatsync.defaultRegistry
import com.mongodb.stitch.android.examples.chatsync.remoteClient
import com.mongodb.stitch.android.examples.chatsync.stitch
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection
import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.DefaultSyncConflictResolvers
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.withContext
import org.bson.BsonString
import org.bson.Document
import org.bson.codecs.pojo.annotations.BsonCreator
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonProperty
import kotlin.coroutines.coroutineContext

@Parcelize
data class User @BsonCreator constructor(
    @BsonId val id: String,
    @BsonProperty("name") val name: String,
    @BsonProperty("lastOnline") val lastOnline: Long,
    @BsonProperty("defaultAvatarOrdinal") val defaultAvatarOrdinal: Int,
    @BsonProperty("avatar") val avatar: Array<Byte>?,
    @BsonProperty("channelsSubscribedTo") val channelsSubscribedTo: List<String>
) : Parcelable {
    companion object {
        private val collection: RemoteMongoCollection<User> by lazy {
            remoteClient
                .getDatabase("chats")
                .getCollection("users", User::class.java)
                .withCodecRegistry(defaultRegistry)
        }

        suspend fun <T> configure(listener: T, exceptionListener: ExceptionListener? = null): Void?
            where T : ChangeEventListener<User> =
            withContext(coroutineContext) {
                Tasks.await(collection.sync().configure(
                    DefaultSyncConflictResolvers.remoteWins(), listener, exceptionListener))
            }

        suspend fun getCurrentUser(): User = withContext(coroutineContext) {
            Tasks.await(collection.sync().find(Document("_id", stitch.auth.user!!.id)).first())
        }

        suspend fun getUser(userId: String): User? = withContext(coroutineContext) {
            Tasks.await(collection.sync().find(Document(mapOf("_id" to userId))).first())
        }

        suspend fun sync(vararg userIds: String) = withContext(coroutineContext) {
            Tasks.await(collection.sync().syncMany(*userIds.map { BsonString(it) }.toTypedArray()))
        }

        suspend fun setCurrentUser(user: User) = withContext(coroutineContext) {
            Tasks.await(collection.sync().insertOne(user))
        }
    }
}
