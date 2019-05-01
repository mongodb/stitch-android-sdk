package com.mongodb.stitch.android.examples.chatsync.model

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Parcelable
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import org.bson.codecs.pojo.annotations.BsonCreator
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonIgnore
import org.bson.codecs.pojo.annotations.BsonProperty

@Parcelize
data class User @BsonCreator constructor(
    @BsonId val id: String,
    @BsonProperty("name") val name: String,
    @BsonProperty("lastOnline") val lastOnline: Long,
    @BsonProperty("defaultAvatarOrdinal") val defaultAvatarOrdinal: Int,
    @BsonProperty("avatar") val avatar: ByteArray?,
    @BsonProperty("channelsSubscribedTo") val channelsSubscribedTo: List<String>
) : Parcelable {
    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is User && id == other.id
    }

    @IgnoredOnParcel
    @BsonIgnore
    lateinit var _bitmapAvatar: Bitmap

    fun bitmapAvatar(): Bitmap {
        if (!::_bitmapAvatar.isInitialized) {
            _bitmapAvatar = BitmapFactory.decodeByteArray(avatar, 0, avatar?.count() ?: 0)
        }
        return _bitmapAvatar
    }
}
