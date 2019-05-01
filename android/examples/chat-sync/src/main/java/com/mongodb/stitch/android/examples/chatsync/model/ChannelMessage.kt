package com.mongodb.stitch.android.examples.chatsync.model

import android.os.Parcelable
import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.examples.chatsync.defaultRegistry
import com.mongodb.stitch.android.examples.chatsync.remoteClient
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCursor
import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.DefaultSyncConflictResolvers
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.SyncConfiguration
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.withContext
import org.bson.BsonObjectId
import org.bson.Document
import org.bson.codecs.pojo.annotations.BsonCreator
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonProperty
import org.bson.types.ObjectId
import kotlin.coroutines.coroutineContext

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

    companion object {
        private val collection: RemoteMongoCollection<ChannelMessage> =
            remoteClient
                .getDatabase("chats")
                .getCollection("messages", ChannelMessage::class.java)
                .withCodecRegistry(defaultRegistry)

        suspend fun getMessages(channelId: String): RemoteMongoCursor<ChannelMessage> =
            withContext(coroutineContext) {
                Tasks.await(collection.sync().find(
                    Document(mapOf("channelId" to channelId)))
                    .sort(Document("sentAt", -1)).iterator())
            }

        suspend fun getMessagesCount(channelId: String): Long = withContext(coroutineContext) {
            Tasks.await(collection.sync().count(Document(mapOf("channelId" to channelId))))
        }

        suspend fun syncMessages(vararg messageIds: String): Void? = withContext(coroutineContext) {
            Tasks.await(collection.sync().syncMany(
                *messageIds.map { BsonObjectId(ObjectId(it)) }.toTypedArray()))
        }

        suspend fun <T> configure(listener: T, exceptionListener: ExceptionListener? = null): Void?
            where T : ChangeEventListener<ChannelMessage> =
            withContext(coroutineContext) {
                Tasks.await(collection.sync().configure(
                    SyncConfiguration.Builder()
                        .withConflictHandler(
                            DefaultSyncConflictResolvers.remoteWins<ChannelMessage>())
                        .withChangeEventListener(listener)
                        .withExceptionListener(exceptionListener)
                        .build()))
            }

        suspend fun fetchMessageIdsFromVector(channelId: String,
                                              localTimestamp: Long,
                                              remoteTimestamp: Long): Array<String> =
            withContext(coroutineContext) {
                Tasks.await(
                    collection.withDocumentClass(Document::class.java).find(
                        Document(mapOf(
                            "channelId" to channelId,
                            "remoteTimestamp" to mapOf(
                                "\$gt" to localTimestamp,
                                "\$lte" to remoteTimestamp
                            )
                        )))
                    .projection(Document(mapOf("_id" to true)))
                    .map { (it["_id"] as ObjectId).toHexString() }
                    .into(mutableListOf<String>()))
                    .toTypedArray()
            }

        suspend fun sendMessage(channelId: String, content: String): ChannelMessage =
            withContext(coroutineContext) {
                val channelMessage = ChannelMessage(
                    ObjectId(), User.getCurrentUser().id, channelId, content, System.currentTimeMillis())
                Tasks.await(collection.sync().insertOne(channelMessage))
                channelMessage
            }
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is ChannelMessage && id == other.id
    }

    override fun compareTo(other: ChannelMessage): Int {
        return this.sentAt.compareTo(other.sentAt) * -1
    }
}
