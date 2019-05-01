package com.mongodb.stitch.android.examples.chatsync.model

import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.examples.chatsync.defaultRegistry
import com.mongodb.stitch.android.examples.chatsync.remoteClient
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection
import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.SyncConfiguration
import kotlinx.coroutines.withContext
import org.bson.BsonString
import org.bson.Document
import org.bson.codecs.pojo.annotations.BsonCreator
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonProperty
import kotlin.coroutines.coroutineContext

/**
 * ChannelMembers are anchored to a [Channel] by their id. They act as a live list of users
 * that have subscribed to the channel. When a user subscribes to channel, Stitch will add
 * them as a member to the associated [ChannelMembers] document.
 *
 * @param id the id of the channel
 * @param members the [User] ids subscribed to this channel
 */
data class ChannelMembers @BsonCreator constructor(
    @BsonId val id: String,
    @BsonProperty("members") val members: List<String>) {

    companion object {
        private val collection: RemoteMongoCollection<ChannelMembers> by lazy {
            remoteClient
                .getDatabase("chats")
                .getCollection("channel_members", ChannelMembers::class.java)
                .withCodecRegistry(defaultRegistry)
        }

        /**
         * Sync a channel.
         *
         * @param channelId the id of the channel to synchronize members on
         */
        suspend fun sync(channelId: String): Void? = withContext(coroutineContext) {
            Tasks.await(collection.sync().syncOne(BsonString(channelId)))
        }

        /**
         * Configure the collection.
         *
         * @param listener the listener that will receive ChangeEvents
         * @param exceptionListener the listener that will receive exceptions
         */
        suspend fun <T> configure(listener: T, exceptionListener: ExceptionListener? = null): Void?
            where T : ConflictHandler<ChannelMembers>, T : ChangeEventListener<ChannelMembers> =
            withContext(coroutineContext) {
                Tasks.await(collection.sync().configure(
                    SyncConfiguration.Builder()
                        .withConflictHandler(listener)
                        .withChangeEventListener(listener)
                        .withExceptionListener(exceptionListener)
                        .build()))
            }
    }
}
