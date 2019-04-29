package com.mongodb.stitch.android.examples.chatsync.model

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.graphics.BitmapFactory
import android.widget.ImageView
import com.google.android.gms.tasks.Tasks
import com.mongodb.stitch.android.examples.chatsync.defaultRegistry
import com.mongodb.stitch.android.examples.chatsync.remoteClient
import com.mongodb.stitch.android.examples.chatsync.stitch
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection
import kotlinx.coroutines.withContext
import org.bson.BsonBinary
import org.bson.Document
import org.bson.codecs.pojo.annotations.BsonCreator
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonProperty
import kotlin.coroutines.coroutineContext

data class User @BsonCreator constructor(
    @BsonId val id: String,
    @BsonProperty("name") val name: String,
    @BsonProperty("lastOnline") val lastOnline: Long,
    @BsonProperty("defaultAvatar") val defaultAvatar: Int,
    @BsonProperty("avatar") val avatar: BsonBinary?,
    @BsonProperty("channelsSubscribedTo") val channelsSubscribedTo: List<String>
) {
    companion object {
        val collection: RemoteMongoCollection<User> by lazy {
            remoteClient
                .getDatabase("chats")
                .getCollection("users", User::class.java)
                .withCodecRegistry(defaultRegistry)
        }

        suspend fun getCurrentUser(): User = withContext(coroutineContext) {
            Tasks.await(collection.sync().find(
                Document("_id", stitch.auth.user!!.id)
            ).first())
        }

        fun getUser(userId: String): LiveData<User> {
            val data = MutableLiveData<User>()
            collection.sync().find(
                Document(mapOf("_id" to userId))
            ).first().addOnSuccessListener {
                data.setValue(it)
            }
            return data
        }
    }

    fun applyAvatar(imageView: ImageView) {
        if (avatar != null) {
            BitmapFactory.decodeByteArray(avatar.data, 0, avatar.data.count())
        } else {
            imageView.setImageResource(defaultAvatar)
        }
    }
}
