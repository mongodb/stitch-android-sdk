package com.mongodb.stitch.android.examples.chatsync.model

import android.os.Parcelable
import org.bson.codecs.pojo.annotations.BsonCreator
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonProperty
import kotlinx.android.parcel.Parcelize

/**
 * A channel for chatting to other [User]s. Channels act as an anchor for our domain.
 *
 * @param id the human readable name of the channel that acts as the [BsonId]
 * @param topic the topic or purpose of this channel
 */
@Parcelize
data class Channel @BsonCreator constructor(
    @BsonId val id: String,
    @BsonProperty("topic") val topic: String) : Parcelable
