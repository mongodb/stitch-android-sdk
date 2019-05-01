package com.mongodb.stitch.android.examples.chatsync.model

import android.os.Parcelable
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection
import org.bson.Document
import org.bson.codecs.pojo.annotations.BsonCreator
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonProperty
import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.examples.chatsync.defaultRegistry
import com.mongodb.stitch.android.examples.chatsync.remoteClient
import com.mongodb.stitch.android.examples.chatsync.repo.ChannelSubscriptionRepo
import com.mongodb.stitch.android.examples.chatsync.stitch
import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.DefaultSyncConflictResolvers
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.SyncConfiguration
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.withContext
import org.bson.BsonString
import org.bson.types.ObjectId
import kotlin.coroutines.coroutineContext

/**
 * A channel for chatting to other [User]s. Channels act as an anchor for our domain.
 *
 * @param id the human readable name of the channel that acts as the [BsonId]
 * @param topic the topic or purpose of this channel
 */
@Parcelize
data class Channel @BsonCreator constructor(
    @BsonId val id: String,
    @BsonProperty("topic") val topic: String) : Parcelable {

    companion object {
        private val collection: RemoteMongoCollection<Channel> by lazy {
            remoteClient
                .getDatabase("chats")
                .getCollection("channels", Channel::class.java)
                .withCodecRegistry(defaultRegistry)
        }

        /**
         * Subscribe to a channel. This will allow a [User] to receive updates
         * from a channel on a given device
         *
         * @param userId the id of the user
         * @param deviceId the deviceId of the user
         * @param channelId the id of the channel to subscribe to
         */
        suspend fun subscribeToChannel(userId: String,
                                       deviceId: String,
                                       channelId: String): ObjectId =
            withContext(coroutineContext) {
                // call the subscribeToChannel function within stitch.
                // see the exported Stitch app for details
                val subscriptionId = Tasks.await(stitch.callFunction(
                    "subscribeToChannel",
                    listOf(userId, deviceId, channelId),
                    ObjectId::class.java))
                // sync the new subscription id so that we can
                // keep tabs on the channel
                ChannelSubscriptionRepo.sync(subscriptionId)
                subscriptionId
            }

        /**
         * Find a channel from the local database.
         *
         * @param channelId the id of the channel to find
         * @return the found [Channel], or null if not
         */
        suspend fun findLocalChannel(channelId: String): Channel? = withContext(coroutineContext) {
            Tasks.await(collection.sync().find(Document(mapOf("_id" to channelId))).first())
        }

        /**
         * Find a channel from the remote database.
         *
         * @param channelId the id of the channel to find
         * @return the found [Channel], or null if not
         */
        suspend fun findRemoteChannel(channelId: String): Channel? = withContext(coroutineContext) {
            Tasks.await(collection.findOne(Document(mapOf("_id" to channelId))))
        }

        /**
         * Sync a channel.
         *
         * @param channelId the id of the channel to synchronize
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
            where T : ChangeEventListener<Channel> = withContext(coroutineContext) {
            Tasks.await(collection.sync().configure(
                SyncConfiguration.Builder()
                    .withConflictHandler(DefaultSyncConflictResolvers.remoteWins<Channel>())
                    .withChangeEventListener(listener)
                    .withExceptionListener(exceptionListener)
                    .build()))
        }
    }
}
