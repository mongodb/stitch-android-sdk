package com.mongodb.stitch.android.examples.chatsync.repo

import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.examples.chatsync.defaultRegistry
import com.mongodb.stitch.android.examples.chatsync.model.ChannelSubscription
import com.mongodb.stitch.android.examples.chatsync.remoteClient
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncUpdateResult
import kotlinx.coroutines.withContext
import org.bson.BsonObjectId
import org.bson.BsonValue
import org.bson.Document
import org.bson.types.ObjectId
import kotlin.coroutines.coroutineContext

object ChannelSubscriptionRepo : SyncRepo<ChannelSubscription, ObjectId>(1) {
    override val collection: RemoteMongoCollection<ChannelSubscription> by lazy {
        remoteClient
            .getDatabase("chats")
            .getCollection("channel_subscriptions", ChannelSubscription::class.java)
            .withCodecRegistry(defaultRegistry)
    }

    override fun idToBson(id: ObjectId): BsonValue = BsonObjectId(id)

    suspend fun getLocalChannelSubscriptionId(
        userId: String,
        deviceId: String,
        channelId: String
    ): ObjectId? =
        withContext(coroutineContext) {
            Tasks.await(collection.withDocumentClass(Document::class.java).sync().find(
                Document(mapOf(
                    "ownerId" to userId,
                    "deviceId" to deviceId,
                    "channelId" to channelId))
            ).projection(Document(mapOf("_id" to true)))
                .map { (it["_id"] as ObjectId) }
                .first())
        }

    suspend fun updateLocalVector(
        channelSubscriptionId: ObjectId,
        localTimestamp: Long
    ): SyncUpdateResult =
        withContext(coroutineContext) {
            Tasks.await(collection.sync().updateOne(
                Document(mapOf("_id" to channelSubscriptionId)),
                Document(mapOf("\$set" to mapOf(
                    ChannelSubscription.KEY_LOCAL_TIMESTAMP to localTimestamp
                )))
            ))
        }
}