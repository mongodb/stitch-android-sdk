package com.mongodb.stitch.android.examples.chatsync.repo

import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.examples.chatsync.defaultRegistry
import com.mongodb.stitch.android.examples.chatsync.model.Channel
import com.mongodb.stitch.android.examples.chatsync.model.User
import com.mongodb.stitch.android.examples.chatsync.remoteClient
import com.mongodb.stitch.android.examples.chatsync.stitch
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection
import kotlinx.coroutines.withContext
import org.bson.BsonString
import org.bson.Document
import org.bson.types.ObjectId
import kotlin.coroutines.coroutineContext

object ChannelRepo : SyncRepo<Channel, String>(100) {
    override val collection: RemoteMongoCollection<Channel> by lazy {
        remoteClient
            .getDatabase("chats")
            .getCollection("channels", Channel::class.java)
            .withCodecRegistry(defaultRegistry)
    }

    override fun idToBson(id: String) = BsonString(id)

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
     * Find a channel from the remote database.
     *
     * @param channelId the id of the channel to find
     * @return the found [Channel], or null if not
     */
    suspend fun findRemoteChannel(channelId: String): Channel? = withContext(coroutineContext) {
        Tasks.await(collection.findOne(Document(mapOf("_id" to channelId))))
    }
}