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
import com.mongodb.stitch.android.examples.chatsync.stitch
import com.mongodb.stitch.core.services.mongodb.remote.ExceptionListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener
import com.mongodb.stitch.core.services.mongodb.remote.sync.DefaultSyncConflictResolvers
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.withContext
import org.bson.BsonString
import org.bson.types.ObjectId
import kotlin.coroutines.coroutineContext

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

        suspend fun subscribeToChannel(userId: String,
                                       deviceId: String,
                                       channelId: String): ObjectId =
            withContext(coroutineContext) {
                val id = Tasks.await(stitch.callFunction(
                    "subscribeToChannel",
                    listOf(userId, deviceId, channelId),
                    ObjectId::class.java))
                ChannelSubscription.sync(id.toHexString())
                id
            }

        suspend fun getLocalChannel(channelId: String): Channel? = withContext(coroutineContext) {
            Tasks.await(collection.sync().find(
                Document(mapOf("_id" to channelId))
            ).first())
        }

        suspend fun getRemoteChannel(channelId: String): Channel? = withContext(coroutineContext) {
            Tasks.await(collection.findOne(Document(mapOf("_id" to channelId))))
        }

        suspend fun sync(channelId: String): Void? = withContext(coroutineContext) {
            Tasks.await(collection.sync().syncOne(BsonString(channelId)))
        }

        suspend fun <T> configure(listener: T, exceptionListener: ExceptionListener? = null): Void?
            where T : ChangeEventListener<Channel> = withContext(coroutineContext) {
            Tasks.await(collection.sync().configure(
                DefaultSyncConflictResolvers.remoteWins(), listener, exceptionListener))
        }
    }
}
