package com.mongodb.stitch.android.examples.chatsync.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import org.bson.codecs.pojo.annotations.BsonCreator
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonProperty
import org.bson.types.ObjectId

/**
 * ChannelMessages are anchored to a [Channel] by channelId. When a new [ChannelMessage]
 * document is inserted, a `channelMessageHasInserted` function will be triggered by the
 * `channelMessageHasInserted` trigger. This function will update every [ChannelSubscription]
 * associated with the [Channel] to have a synchronized remoteTimestamp. It will then update
 * the [ChannelMessage] with a fresh `sentAt` parameter, and the associated `remoteTimestamp`.
 *
 * @param id the unique id of the message
 * @param channelId the id of the channel this was sent to
 * @param content the actual text of the message
 * @param sentAt the time the message was sent to the server or the time the message hit the server
 * @param remoteTimestamp the logical time of the message
 */
@Parcelize
data class ChannelMessage @BsonCreator constructor(
    @BsonId val id: ObjectId,
    @BsonProperty("ownerId") val ownerId: String,
    @BsonProperty("channelId") val channelId: String,
    @BsonProperty("content") val content: String,
    @BsonProperty("sentAt") val sentAt: Long,
    @BsonProperty("remoteTimestamp") val remoteTimestamp: Long? = null) :
    Comparable<ChannelMessage>, Parcelable {

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is ChannelMessage && id == other.id
    }

    override fun compareTo(other: ChannelMessage): Int {
        if (this == other) {
            return 0
        }
        return this.sentAt.compareTo(other.sentAt) * -1
    }
}
