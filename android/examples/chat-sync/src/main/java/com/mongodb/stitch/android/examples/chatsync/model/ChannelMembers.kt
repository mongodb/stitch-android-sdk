package com.mongodb.stitch.android.examples.chatsync.model

import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.examples.chatsync.defaultRegistry
import com.mongodb.stitch.android.examples.chatsync.remoteClient
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection
import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler
import kotlinx.coroutines.withContext
import org.bson.codecs.pojo.annotations.BsonCreator
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonProperty
import kotlin.coroutines.coroutineContext

data class ChannelMembers @BsonCreator constructor(
    @BsonId val id: String,
    @BsonProperty("members") val members: List<User>) {

    companion object {
        private val collection: RemoteMongoCollection<ChannelMembers> by lazy {
            remoteClient
                .getDatabase("chats")
                .getCollection("channel_members", ChannelMembers::class.java)
                .withCodecRegistry(defaultRegistry)
        }

        suspend fun <T> configure(listener: T, exceptionListener: ExceptionListener? = null): Void?
            where T : ConflictHandler<ChannelMembers>, T : ChangeEventListener<ChannelMembers> =
            withContext(coroutineContext) {
                Tasks.await(collection.sync().configure(listener, listener, exceptionListener))
            }
    }
}
