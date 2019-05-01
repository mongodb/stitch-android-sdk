package com.mongodb.stitch.android.examples.chatsync.model

import org.bson.codecs.pojo.annotations.BsonCreator
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonProperty
import org.bson.types.ObjectId

/**
 * ChannelSubscriptions are anchored to a [Channel] by the channelId.
 * When subscribing to a channel, the [ObjectId] of the channel subscription will be returned, which
 * we will then synchronize on.
 *
 * The [ChannelSubscription] acts as a vector clock. Whenever a [ChannelMessage] is sent to the
 * server, the server updates each [ChannelSubscription.remoteTimestamp] associated with the
 * channel. The [com.mongodb.stitch.android.examples.chatsync.service.ChannelService] will,
 * when online, receive this update, and fetch all messages between the two points in the vector
 * ([ChannelSubscription.localTimestamp] and [ChannelSubscription.remoteTimestamp]. Once completed,
 * the local timestamp will be updated and sent to the server.
 *
 * [User]s have one subscription per device per channel.
 *
 * @param id the unique id of this subscription
 * @param ownerId the user id of the owner of this subscription
 * @param deviceId the device id of the owner of this subscription
 * @param localTimestamp the local logical time
 * @param remoteTimestamp the remote logical time
 */
data class ChannelSubscription @BsonCreator constructor(
    @BsonId val id: ObjectId,
    @BsonProperty("channelId") val channelId: String,
    @BsonProperty("ownerId") val ownerId: String,
    @BsonProperty("deviceId") val deviceId: String,
    @BsonProperty(KEY_LOCAL_TIMESTAMP) val localTimestamp: Long,
    @BsonProperty(KEY_REMOTE_TIMESTAMP) val remoteTimestamp: Long) {

    companion object {
        const val KEY_LOCAL_TIMESTAMP = "localTimestamp"
        const val KEY_REMOTE_TIMESTAMP = "remoteTimestamp"
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is ChannelSubscription && id == other.id
    }
}
