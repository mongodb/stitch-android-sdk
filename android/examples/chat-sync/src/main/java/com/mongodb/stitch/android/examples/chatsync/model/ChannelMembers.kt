package com.mongodb.stitch.android.examples.chatsync.model

import org.bson.codecs.pojo.annotations.BsonCreator
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonProperty

/**
 * ChannelMembers are anchored to a [Channel] by their id. They act as a live list of users
 * that have subscribed to the channel. When a user subscribes to channel, Stitch will add
 * them as a member to the associated [ChannelMembers] document.
 *
 * @param id the id of the channel
 * @param members the [User] ids subscribed to this channel
 */
data class ChannelMembers @BsonCreator constructor(
    @BsonId val id: String,
    @BsonProperty("members") val members: List<String>)
