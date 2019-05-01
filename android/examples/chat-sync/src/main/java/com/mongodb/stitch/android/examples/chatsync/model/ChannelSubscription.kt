package com.mongodb.stitch.android.examples.chatsync.model

import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.examples.chatsync.defaultRegistry
import com.mongodb.stitch.android.examples.chatsync.remoteClient
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection
import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ConflictHandler
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncUpdateResult
import com.mongodb.stitch.core.services.mongodb.remote.sync.internal.SyncConfiguration
import kotlinx.coroutines.withContext
import org.bson.BsonObjectId
import org.bson.Document
import org.bson.codecs.pojo.annotations.BsonCreator
import org.bson.codecs.pojo.annotations.BsonProperty
import org.bson.types.ObjectId
import kotlin.coroutines.coroutineContext

data class ChannelSubscription @BsonCreator constructor(
    @BsonProperty("channelId") val channelId: String,
    @BsonProperty("ownerId") val ownerId: String,
    @BsonProperty("deviceId") val deviceId: String,
    @BsonProperty(KEY_LOCAL_TIMESTAMP) val localTimestamp: Long,
    @BsonProperty(KEY_REMOTE_TIMESTAMP) val remoteTimestamp: Long) {

    companion object {
        const val KEY_LOCAL_TIMESTAMP = "localTimestamp"
        const val KEY_REMOTE_TIMESTAMP = "remoteTimestamp"

        private val collection: RemoteMongoCollection<ChannelSubscription> by lazy {
            remoteClient
                .getDatabase("chats")
                .getCollection("channel_subscriptions", ChannelSubscription::class.java)
                .withCodecRegistry(defaultRegistry)
        }

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

        suspend fun getLocalChannelSubscription(channelSubscriptionId: String): ChannelSubscription? =
            withContext(coroutineContext) {
                Tasks.await(collection.sync().find(
                    Document(mapOf("_id" to ObjectId(channelSubscriptionId)))
                ).first())
            }

        suspend fun setChannelSubscriptionLocalVector(
            channelSubscriptionId: String,
            localTimestamp: Long
        ): SyncUpdateResult =
            withContext(coroutineContext) {
                Tasks.await(collection.sync().updateOne(
                    Document(mapOf("_id" to ObjectId(channelSubscriptionId))),
                    Document(mapOf("\$set" to mapOf(KEY_LOCAL_TIMESTAMP to localTimestamp)))
                ))
            }

        suspend fun sync(channelSubscriptionId: String): Void? = withContext(coroutineContext) {
            Tasks.await(collection.sync().syncOne(BsonObjectId(ObjectId(channelSubscriptionId))))
        }

        suspend fun <T> configure(
            listener: T, exceptionListener: ExceptionListener? = null
        ): Void? where T : ConflictHandler<ChannelSubscription>,
                       T : ChangeEventListener<ChannelSubscription> =
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
