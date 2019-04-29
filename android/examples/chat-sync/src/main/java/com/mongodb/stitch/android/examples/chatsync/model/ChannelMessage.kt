package com.mongodb.stitch.android.examples.chatsync.model

import android.os.Parcelable
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.examples.chatsync.defaultRegistry
import com.mongodb.stitch.android.examples.chatsync.remoteClient
import com.mongodb.stitch.android.examples.chatsync.user
import com.mongodb.stitch.android.examples.chatsync.viewModel.SubscriptionId
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCursor
import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.DefaultSyncConflictResolvers
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncInsertOneResult
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.withContext
import org.bson.BsonObjectId
import org.bson.Document
import org.bson.codecs.pojo.annotations.BsonCreator
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonProperty
import org.bson.types.ObjectId
import kotlin.coroutines.coroutineContext

@Parcelize
data class ChannelMessage @BsonCreator constructor(
    @BsonId val id: ObjectId,
    @BsonProperty("ownerId") val ownerId: String,
    @BsonProperty("channelId") val channelId: String,
    @BsonProperty("content") val content: String,
    @BsonProperty("sentAt") val sentAt: Long? = null,
    @BsonProperty("remoteTimestamp") val remoteTimestamp: Long? = null) :
    Comparable<ChannelMessage>, Parcelable {

    companion object {
        private val collection: RemoteMongoCollection<ChannelMessage> =
            remoteClient.getDatabase("chats").getCollection(
                "messages",
                ChannelMessage::class.java).withCodecRegistry(defaultRegistry)

        fun getMessages(channelId: String): Task<RemoteMongoCursor<ChannelMessage>> {
            return collection.sync().find(Document(mapOf("channelId" to channelId))).iterator()
        }

        suspend fun syncMessages(vararg messageIds: String): Void? = withContext(coroutineContext) {
            Tasks.await(collection.sync().syncMany(
                *messageIds.map { BsonObjectId(ObjectId(it)) }.toTypedArray()))
        }

        suspend fun <T> configure(listener: T, exceptionListener: ExceptionListener? = null): Void?
            where T : ChangeEventListener<ChannelMessage> =
            withContext(coroutineContext) {
                Tasks.await(collection.sync().configure(
                    DefaultSyncConflictResolvers.remoteWins(), listener, exceptionListener))
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

        suspend fun sendMessage(
            channelId: String, channelSubscriptionId: SubscriptionId, content: String
        ): ChannelMessage =
            withContext(coroutineContext) {
                val channelMessage = ChannelMessage(
                    ObjectId(), User.getCurrentUser().id, channelId, content)
                Tasks.await(collection.sync().insertOne(channelMessage))
                // increment the channel monitor local vector
                ChannelSubscription.incrementChannelSubscriptionLocalVector(channelSubscriptionId)
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
        return when {
            this.sentAt == null && other.sentAt == null -> 0
            this.sentAt == null && other.sentAt != null -> -1
            this.sentAt != null && other.sentAt == null -> 1
            else -> other.sentAt!!.compareTo(this.sentAt!!) * -1
        }
    }
}
