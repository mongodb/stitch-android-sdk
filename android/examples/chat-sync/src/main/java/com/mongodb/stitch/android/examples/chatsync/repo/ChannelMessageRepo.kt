package com.mongodb.stitch.android.examples.chatsync.repo

import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.examples.chatsync.defaultRegistry
import com.mongodb.stitch.android.examples.chatsync.model.ChannelMessage
import com.mongodb.stitch.android.examples.chatsync.remoteClient
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCursor
import kotlinx.coroutines.withContext
import org.bson.BsonObjectId
import org.bson.Document
import org.bson.types.ObjectId
import kotlin.coroutines.coroutineContext

object ChannelMessageRepo : SyncRepo<ChannelMessage, ObjectId>(10) {
    override val collection: RemoteMongoCollection<ChannelMessage> =
        remoteClient
            .getDatabase("chats")
            .getCollection("messages", ChannelMessage::class.java)
            .withCodecRegistry(defaultRegistry)

    override fun idToBson(id: ObjectId) = BsonObjectId(id)

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
                ObjectId(),
                UserRepo.findCurrentUser()!!.id,
                channelId,
                content,
                System.currentTimeMillis())
            Tasks.await(collection.sync().insertOne(channelMessage))
            channelMessage
        }
}